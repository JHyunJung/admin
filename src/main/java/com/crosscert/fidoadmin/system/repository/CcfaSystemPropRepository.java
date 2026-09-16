package com.crosscert.fidoadmin.system.repository;

import com.crosscert.fidoadmin.system.entity.CcfaSystemProp;
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CcfaSystemPropRepository extends JpaRepository<CcfaSystemProp, CcfaSystemPropId>, JpaSpecificationExecutor<CcfaSystemProp> {
}
