package com.crosscert.fidoadmin.audit;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.auth.Sha256PasswordEncoder;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.log.entity.CcfaAuditLog;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
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

    /** 현재 로그인 사용자·현재 HTTP 요청 기준으로 기록한다. 로그인 사용자가 없으면 기록하지 않는다. */
    public void log(AuditType type, String message) {
        TenantContext.current().ifPresent(actor -> {
            HttpServletRequest req = currentRequest();
            log(actor, type, message, req == null ? null : req.getRemoteAddr(),
                req == null ? null : req.getHeader("User-Agent"));
        });
    }

    public void log(ManagerUserDetails actor, AuditType type, String message, String ip, String ua) {
        try {
            CcfaAuditLog row = new CcfaAuditLog();
            row.setCompanyIdx(actor.getCompanyIdx());
            row.setCompanyName(cut(actor.getCompanyName(), 512));
            row.setType(type.name());
            row.setUserId(cut(actor.getUserId(), 64));
            row.setUserName(cut(actor.getUserNm(), 32));
            row.setMessage(cut(message, 4000));
            row.setIp(cut(ip, 15));
            row.setUa(cut(ua, 2048));
            row.setCreatedtime(LocalDateTime.now());
            row.setIntergrityHash(Sha256PasswordEncoder.sha256Hex(String.join("|",
                String.valueOf(row.getCompanyIdx()), row.getCompanyName(), row.getType(), row.getUserId(),
                row.getUserName(), row.getMessage(), String.valueOf(row.getIp()), String.valueOf(row.getUa()),
                String.valueOf(row.getCreatedtime()))));
            writer.write(row);
        } catch (RuntimeException e) {
            log.error("감사 로그 기록 실패: type={} message={}", type, message, e);
        }
    }

    private static String cut(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes sra ? sra.getRequest() : null;
    }
}
