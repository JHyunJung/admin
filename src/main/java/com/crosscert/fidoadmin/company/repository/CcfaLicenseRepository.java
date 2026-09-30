package com.crosscert.fidoadmin.company.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.company.entity.CcfaLicense;

public interface CcfaLicenseRepository extends AdminRepository<CcfaLicense, Long> {

    /** 외부 라이선스 조회. HASHVALUE 가 겹치면 먼저 만든 행(IDX 최소)을 준다. */
    java.util.Optional<CcfaLicense> findFirstByHashvalueOrderByIdxAsc(String hashvalue);
}
