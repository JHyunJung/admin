package com.crosscert.fidoadmin.company.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import java.util.Optional;

public interface CcfaCompanyRepository extends AdminRepository<CcfaCompany, Long> {

    /** VENDOR_CODE 에 유니크 제약이 없어 중복이 있을 수 있다. 하나만 받아 화면 검증에 쓴다. */
    Optional<CcfaCompany> findFirstByVendorCode(String vendorCode);
}
