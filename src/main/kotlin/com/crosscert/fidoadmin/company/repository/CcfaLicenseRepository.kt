package com.crosscert.fidoadmin.company.repository

import com.crosscert.fidoadmin.common.AdminRepository
import com.crosscert.fidoadmin.company.entity.CcfaLicense
import java.util.Optional

interface CcfaLicenseRepository : AdminRepository<CcfaLicense, Long> {

    /** 외부 라이선스 조회. HASHVALUE 가 겹치면 먼저 만든 행(IDX 최소)을 준다. */
    fun findFirstByHashvalueOrderByIdxAsc(hashvalue: String?): Optional<CcfaLicense>

    /** 고객사 삭제 전 하위 데이터 검사. */
    fun countByCompanyIdx(companyIdx: Long?): Long
}
