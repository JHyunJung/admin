package com.crosscert.fidoadmin.signup;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.common.ManagerStatus;
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import jakarta.persistence.EntityManager;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 가입 신청 저장과 승인·거절. 신청은 인증 없이 호출되므로 {@code common.CrudService} 를
 * 상속하지 않는다. {@code CrudService.create()} 는 테넌트가 있는 테이블이면
 * {@code tenant.companyIdx()} 를 부르고, 이는 {@code require()} 로 이어져
 * 로그인 사용자가 없으면 예외를 던진다.
 *
 * <p>감사 로그는 {@code apply()} 에만 남지 않는다. AuditLogger 는 로그인 사용자가 없으면
 * 조용히 기록을 건너뛰는데, 신청은 익명 호출이기 때문이다(의도된 동작, 설계서 참조).
 * 승인·거절은 로그인한 슈퍼 관리자가 수행하므로 정상적으로 기록된다.
 */
@Service
@RequiredArgsConstructor
public class SignupService {

    /** CCFA_MANAGER.ETC 의 크기. 문자 수가 아니라 <b>바이트</b> 다(한글 1자 = 3바이트). */
    private static final int ETC_MAX_BYTES = 2048;

    private final CcfaManagerRepository managers;
    private final CcfaCompanyRepository companies;
    private final EntityManager em;
    private final AuditLogger audit;

    @Transactional(readOnly = true)
    public boolean existsUserId(String userId) {
        return managers.findByUserId(userId).isPresent();
    }

    /**
     * 신청을 저장한다. 상태와 소속은 입력과 무관하게 강제된다(권한 상승 차단 1단계).
     * 상태·소속을 받을 파라미터 자체가 없으므로 호출자가 값을 끼워 넣을 통로가 없다.
     *
     * <p>USER_ID 에 DB 유니크 제약이 없어 동시 신청 시 중복이 생길 수 있다.
     * {@code ManagerService.insert()} 와 같은 방식으로 테이블을 배타 잠금해 직렬화한다.
     * 가입은 드물게 일어나므로 테이블 잠금 비용은 수용 가능하다.
     */
    @Transactional
    public CcfaManager apply(String userId, String encodedPw, String userNm,
                             String userEmail, String userPhone, String reason) {
        em.createNativeQuery("LOCK TABLE CCFA_MANAGER IN EXCLUSIVE MODE").executeUpdate();
        if (managers.findByUserId(userId).isPresent()) {
            throw new DataIntegrityViolationException("CCFA_MANAGER " + userId + " 은(는) 이미 존재합니다");
        }
        LocalDateTime now = LocalDateTime.now();
        CcfaManager m = new CcfaManager();
        // IDX 는 CCFA_MANAGER_SEQ 가 채번한다. 직접 넣지 않는다.
        m.setUserId(userId);
        m.setUserPw(encodedPw);
        m.setUserNm(userNm);
        m.setUserEmail(userEmail);
        m.setUserPhone(userPhone);
        // 승인 전까지 로그인 불가. 소속은 미배정(-1)이며 SUPER 인 0 이 될 수 없다.
        m.setStatus(SignupPolicy.STATUS_PENDING);
        m.setCompanyIdx(SignupPolicy.UNASSIGNED_COMPANY_IDX);
        m.setLogin("OFF-LINE");
        m.setAlramType("none");
        m.setAlramLevel("0");
        m.setEtc(reason == null || reason.isBlank() ? null : "신청 사유: " + reason);
        m.setCreatedtime(now);
        m.setUpdatedtime(now);
        return managers.save(m);
    }

    /** 승인 대기 목록. 오래된 신청이 위로 온다. */
    @Transactional(readOnly = true)
    public List<CcfaManager> pending() {
        return managers.findByStatusOrderByCreatedtimeAsc(SignupPolicy.STATUS_PENDING);
    }

    /**
     * 승인: 소속을 배정하고 활성화한다.
     *
     * <p>권한 상승 차단 2단계 — 전역(IDX 0) 배정을 거부한다. 승인으로 슈퍼 관리자를
     * 만들 수 없다. 미배정 표식(-1)이나 null 로도 승인할 수 없다.
     * 권한 상승 차단 3단계 — 대상이 승인대기일 때만 동작한다({@link #lockPending}).
     *
     * <p>실재하지 않는 고객사 IDX 도 거부한다. CCFA_MANAGER 에는 CCFA_COMPANY 로 가는
     * 외래키가 없어 DB 가 걸러 주지 않는다. 그대로 두면 활성 계정이 존재하지 않는 테넌트에
     * 묶여 로그인은 되지만 테넌트로 걸러지는 모든 조회가 빈 결과를 돌려주고,
     * 신청은 대기 목록에서 사라져 운영자가 다시 처리할 수도 없다.
     *
     * <p>고객사 존재 확인에는 {@code CompanyLookup} 이 아니라 리포지터리를 직접 쓴다.
     * CompanyLookup 은 로그인 사용자 기준({@code TenantContext})으로 결과를 가리는
     * 화면용 조회라, 없는 고객사와 볼 권한이 없는 고객사를 똑같이 "#IDX" 로 돌려준다.
     * 존재 여부를 판정할 수 없을뿐더러 서비스 계층에 웹 요청 컨텍스트 의존이 끼어든다.
     *
     * <p>모든 검증은 {@link #lockPending} 이 행을 건드리기 전에 끝난다. 거부된 승인은
     * 행을 승인대기 그대로 남긴다.
     */
    @Transactional
    public CcfaManager approve(Long idx, Long companyIdx) {
        if (companyIdx != null && companyIdx == SignupPolicy.SUPER_COMPANY_IDX) {
            throw new IllegalArgumentException("전역(IDX 0) 고객사로는 승인할 수 없습니다.");
        }
        if (companyIdx == null || companyIdx < 0) {
            throw new IllegalArgumentException("고객사를 선택해야 합니다.");
        }
        if (!companies.existsById(companyIdx)) {
            throw new IllegalArgumentException("존재하지 않는 고객사입니다: " + companyIdx);
        }
        CcfaManager m = lockPending(idx);
        m.setCompanyIdx(companyIdx);
        m.setStatus(ManagerStatus.ACTIVE);
        m.setUpdatedtime(LocalDateTime.now());
        CcfaManager saved = managers.save(m);
        audit.log(AuditType.STATUS, "CCFA_MANAGER SIGNUP APPROVE " + saved.getUserId()
            + " -> COMPANY_IDX " + companyIdx);
        return saved;
    }

    /** 거절: 상태를 바꾸고 사유를 ETC 에 덧붙인다. 소속은 미배정 그대로 둔다. */
    @Transactional
    public CcfaManager reject(Long idx, String reason) {
        CcfaManager m = lockPending(idx);
        m.setStatus(SignupPolicy.STATUS_REJECTED);
        m.setEtc(appendReason(m.getEtc(), reason));
        m.setUpdatedtime(LocalDateTime.now());
        CcfaManager saved = managers.save(m);
        audit.log(AuditType.STATUS, "CCFA_MANAGER SIGNUP REJECT " + saved.getUserId());
        return saved;
    }

    /**
     * 대상 행을 FOR UPDATE 로 잠그고 <b>잠근 뒤에</b> 승인대기인지 확인한다.
     * 잠그기 전에 읽으면 동시에 들어온 두 처리가 둘 다 검사를 통과한다.
     * 운영자 잠금 해제({@code ManagerService.unlock})와 같은 방식이다.
     */
    private CcfaManager lockPending(Long idx) {
        CcfaManager m = managers.findByIdxForUpdate(idx)
            .orElseThrow(() -> new IllegalArgumentException("가입 신청을 찾을 수 없습니다: " + idx));
        if (!SignupPolicy.STATUS_PENDING.equals(m.getStatus())) {
            throw new IllegalStateException(
                "이미 처리된 신청입니다(승인대기 상태가 아닙니다): " + m.getUserId());
        }
        return m;
    }

    /**
     * 거절 사유를 ETC 뒤에 덧붙인다. ETC 는 문자 수가 아니라 바이트로 제한되므로
     * 문자 수로 자르면 한글 입력에서 ORA-12899 로 저장이 실패한다.
     */
    private static String appendReason(String etc, String reason) {
        String added = "거절 사유: " + (reason == null || reason.isBlank() ? "(사유 없음)" : reason);
        String merged = etc == null || etc.isBlank() ? added : etc + "\n" + added;
        return truncateToBytes(merged, ETC_MAX_BYTES);
    }

    /**
     * UTF-8 바이트 기준으로 자른다. 경계에서 멀티바이트 문자가 쪼개지면 깨진 글자가
     * 저장되므로, 잘린 바이트를 무시하는 디코더로 문자 경계에서 끊는다.
     */
    private static String truncateToBytes(String s, int maxBytes) {
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
}
