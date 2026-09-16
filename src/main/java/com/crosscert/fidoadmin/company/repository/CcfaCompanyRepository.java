package com.crosscert.fidoadmin.company.repository;

import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CcfaCompanyRepository extends JpaRepository<CcfaCompany, Long>, JpaSpecificationExecutor<CcfaCompany> {
}
