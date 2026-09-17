package com.crosscert.fidoadmin.system.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.system.entity.CcfaOptions;
import java.util.List;

public interface CcfaOptionsRepository extends AdminRepository<CcfaOptions, Long> {
    List<CcfaOptions> findByOptionIdxOrderByIdxAsc(Long optionIdx);
    long countByOptionIdx(Long optionIdx);
}
