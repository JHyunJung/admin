package com.crosscert.fidoadmin.audit;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.auth.Sha256PasswordEncoder;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.log.entity.CcfaAuditLog;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** CCFA_AUDIT_LOG 기록. 실패해도 호출자 트랜잭션을 깨지 않는다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogger {

    private final AuditLogWriter writer;
    private final TenantContext tenant;
    private final CompanyLookup companies;

    /**
     * 현재 로그인 사용자·현재 HTTP 요청 기준으로 기록한다. 로그인 사용자가 없으면 기록하지 않는다.
     *
     * <p>COMPANY_IDX 에는 <b>대상 테넌트</b>를 넣는다. 행위자는 USER_ID 로 식별된다.
     * 슈퍼관리자가 어느 고객사 데이터를 만졌는지가 이 컬럼에만 남는다
     * (스키마를 바꿀 수 없어 기존 컬럼을 이렇게 해석한다).
     * 선택이 없는 전역 작업은 행위자 소속(0)을 그대로 쓴다 — 사실에 맞다.
     */
    public void log(AuditType type, String message) {
        tenant.current().ifPresent(actor -> {
            HttpServletRequest req = currentRequest();
            logResolvingTarget(actor, type, message,
                req == null ? null : req.getRemoteAddr(),
                req == null ? null : req.getHeader("User-Agent"));
        });
    }

    /**
     * 대상 테넌트를 여기서 해석한다({@code companies.name(...)} 의 DB 조회 포함).
     * 해석과 기록을 같은 try/catch 보호 구역에 두어, 해석 실패도 기록 실패와 똑같이
     * 삼켜야 "실패해도 호출자 트랜잭션을 깨지 않는다"는 클래스 불변식이 지켜진다.
     */
    private void logResolvingTarget(ManagerUserDetails actor, AuditType type, String message, String ip, String ua) {
        try {
            Long targetCompany = tenant.hasTenant() ? tenant.companyIdx() : actor.getCompanyIdx();
            String targetName = tenant.hasTenant() ? companies.name(targetCompany) : actor.getCompanyName();
            writeRow(actor, type, message, targetCompany, targetName, ip, ua);
        } catch (RuntimeException e) {
            log.error("감사 로그 기록 실패: type={} message={}", type, message, e);
        }
    }

    /** 로그인·로그아웃처럼 테넌트가 없는 사건용. 행위자 소속을 그대로 쓴다. */
    public void log(ManagerUserDetails actor, AuditType type, String message, String ip, String ua) {
        try {
            writeRow(actor, type, message, actor.getCompanyIdx(), actor.getCompanyName(), ip, ua);
        } catch (RuntimeException e) {
            log.error("감사 로그 기록 실패: type={} message={}", type, message, e);
        }
    }

    private void writeRow(ManagerUserDetails actor, AuditType type, String message,
                          Long companyIdx, String companyName, String ip, String ua) {
        CcfaAuditLog row = new CcfaAuditLog();
        row.setCompanyIdx(companyIdx);
        row.setCompanyName(cut(companyName, 512));
        row.setType(type.name());
        row.setUserId(cut(actor.getUserId(), 64));
        row.setUserName(cut(actor.getUserNm(), 32));
        row.setMessage(cut(message, 4000));
        row.setIp(cut(ip, 15));
        row.setUa(cut(ua, 2048));
        // Oracle TIMESTAMP 은 소수점 6자리(마이크로초)까지만 보존한다.
        // 나노초를 그대로 두면 저장 후 읽은 값으로 해시를 재계산할 수 없다.
        row.setCreatedtime(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
        row.setIntergrityHash(integrityHash(row));
        writer.write(row);
    }

    /**
     * 무결성 해시. 저장된 행에서 동일하게 재계산할 수 있어야 하므로
     * 필드 경계를 길이 접두사로 명시한다(값에 "|" 가 들어와도 모호해지지 않는다).
     * null 과 빈 문자열은 Oracle 이 둘 다 NULL 로 저장하므로 같은 표현으로 맞춘다.
     */
    public static String integrityHash(CcfaAuditLog row) {
        StringBuilder sb = new StringBuilder();
        field(sb, String.valueOf(row.getCompanyIdx()));
        field(sb, row.getCompanyName());
        field(sb, row.getType());
        field(sb, row.getUserId());
        field(sb, row.getUserName());
        field(sb, row.getMessage());
        field(sb, row.getIp());
        field(sb, row.getUa());
        field(sb, row.getCreatedtime() == null ? null : row.getCreatedtime().toString());
        return Sha256PasswordEncoder.sha256Hex(sb.toString());
    }

    /** Oracle 은 빈 문자열을 NULL 로 저장하므로 둘을 같게 본다. */
    private static void field(StringBuilder sb, String value) {
        if (value == null || value.isEmpty()) {
            sb.append("-|");
        } else {
            sb.append(value.length()).append(':').append(value).append('|');
        }
    }

    /**
     * UTF-8 바이트 기준으로 자른다. CCFA_AUDIT_LOG 의 VARCHAR2 는 BYTE 의미라
     * 문자 수로 자르면 한글 입력에서 ORA-12899 가 나고 감사 기록이 유실된다.
     * 멀티바이트 문자가 중간에서 쪼개지지 않도록 문자 경계에서 끊는다.
     */
    private static String cut(String s, int maxBytes) {
        if (s == null) return null;
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) return s;
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.IGNORE)
            .onUnmappableCharacter(CodingErrorAction.IGNORE);
        try {
            return decoder.decode(ByteBuffer.wrap(bytes, 0, maxBytes)).toString();
        } catch (CharacterCodingException e) {
            return s.substring(0, Math.min(s.length(), maxBytes));
        }
    }

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes sra ? sra.getRequest() : null;
    }
}
