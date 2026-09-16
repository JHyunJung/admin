package com.crosscert.fidoadmin.auth;

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

    static final String STATUS_ACTIVE = "활성";

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
        long companyIdx = m.getCompanyIdx() == null ? 0L : m.getCompanyIdx();
        String companyName = companies.findById(companyIdx).map(CcfaCompany::getCompanyName)
            .orElse(companyIdx == 0L ? "전역" : "고객사 " + companyIdx);
        String name = m.getUserNm() == null || m.getUserNm().isBlank() ? m.getUserId() : m.getUserNm();
        return new ManagerUserDetails(m.getIdx(), m.getUserId(), m.getUserPw(), name, companyIdx, companyName,
            STATUS_ACTIVE.equals(m.getStatus()), !locked);
    }
}
