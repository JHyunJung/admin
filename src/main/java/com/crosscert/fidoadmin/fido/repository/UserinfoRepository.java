package com.crosscert.fidoadmin.fido.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.fido.entity.Userinfo;

public interface UserinfoRepository extends AdminRepository<Userinfo, Long> {
    long countByCompanyIdx(Long companyIdx);
}
