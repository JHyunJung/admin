package com.crosscert.fidoadmin.fido.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.fido.entity.Appid;

public interface AppidRepository extends AdminRepository<Appid, Long> {
    long countByCompanyIdx(Long companyIdx);
}
