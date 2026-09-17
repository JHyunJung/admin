package com.crosscert.fidoadmin.auth;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordChangeService {

    private final CcfaManagerRepository managers;
    private final PasswordEncoder encoder;
    private final AuditLogger audit;

    @Transactional
    public void change(String userId, String current, String next) {
        CcfaManager m = managers.findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("운영자를 찾을 수 없습니다."));
        if (!encoder.matches(current, m.getUserPw())) {
            throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
        }
        m.setUserPw(encoder.encode(next));
        m.setUpdatedtime(LocalDateTime.now());
        managers.save(m);
        audit.log(AuditType.UPDATE, "CCFA_MANAGER PASSWORD " + userId);
    }
}
