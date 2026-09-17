package com.crosscert.fidoadmin.auth;

import com.crosscert.fidoadmin.common.ManagerStatus;
import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerPwPolicyRepository;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagerUserDetailsService implements UserDetailsService {

    private final CcfaManagerRepository managers;
    private final CcfaManagerPwPolicyRepository policies;
    private final CcfaCompanyRepository companies;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        CcfaManager m = managers.findByUserId(username)
            .orElseThrow(() -> new UsernameNotFoundException("운영자 없음: " + username));
        boolean locked = policies.findFirstByUserIdOrderByIdxDesc(username)
            .map(p -> "Y".equalsIgnoreCase(p.getAccountLock())).orElse(false);
        // COMPANY_IDX 가 null 이어도 0 으로 치환하지 않는다. 0 은 SUPER 이므로 치환은 권한 상승이다.
        // null 은 ManagerUserDetails 생성자가 거부한다.
        Long companyIdx = m.getCompanyIdx();
        String companyName = companyIdx == null ? null
            : companies.findById(companyIdx).map(CcfaCompany::getCompanyName)
                .orElse(companyIdx == 0L ? "전역" : "고객사 " + companyIdx);
        String name = m.getUserNm() == null || m.getUserNm().isBlank() ? m.getUserId() : m.getUserNm();
        return new ManagerUserDetails(m.getIdx(), m.getUserId(), m.getUserPw(), name, companyIdx, companyName,
            ManagerStatus.ACTIVE.equals(m.getStatus()), !locked);
    }
}
