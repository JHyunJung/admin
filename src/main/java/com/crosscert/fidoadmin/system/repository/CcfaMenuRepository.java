package com.crosscert.fidoadmin.system.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.system.entity.CcfaMenu;
import java.util.List;

public interface CcfaMenuRepository extends AdminRepository<CcfaMenu, Long> {
    long countByMenuParentIdx(Long menuParentIdx);
    List<CcfaMenu> findAllByOrderByMenuParentIdxAscMenuSeqAscIdxAsc();
}
