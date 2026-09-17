package com.crosscert.fidoadmin.system.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.system.entity.CcfaMenu;
import com.crosscert.fidoadmin.system.repository.CcfaMenuRepository;
import com.crosscert.fidoadmin.system.web.MenuSearchForm;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CCFA_MENU — 어드민 메뉴 트리(데이터로만 다룬다). COMPANY_IDX 가 없으므로 SUPER 전용. */
@Service
public class MenuService extends CrudService<CcfaMenu, Long, MenuSearchForm> {

    private final CcfaMenuRepository menus;

    public MenuService(CcfaMenuRepository repository, AuditLogger audit) {
        super(repository, audit);
        this.menus = repository;
    }

    @Override protected Specification<CcfaMenu> toSpecification(MenuSearchForm f) {
        return Specs.all(Specs.like("menuName", f.getMenuName()), Specs.like("menuCode", f.getMenuCode()));
    }
    @Override protected String companyIdxAttribute() { return null; }
    @Override protected Long companyIdxOf(CcfaMenu e) { return null; }
    @Override protected void setCompanyIdx(CcfaMenu e, Long c) {}
    @Override public String idOf(CcfaMenu e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "CCFA_MENU"; }
    @Override public Sort defaultSort() {
        return Sort.by(Sort.Order.asc("menuParentIdx"), Sort.Order.asc("menuSeq"), Sort.Order.asc("idx"));
    }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "menuName", "menuCode", "menuParentIdx", "menuSeq"); }

    @Override protected void applyDefaults(CcfaMenu e) {
        if (e.getMenuParentIdx() == null) e.setMenuParentIdx(0L);
        if (e.getVisible() == null) e.setVisible("true");
        if (e.getOpenType() == null) e.setOpenType("open");
        if (e.getStatistics() == null) e.setStatistics("N");
        if (e.getReadonly() == null) e.setReadonly("N");
    }

    @Override protected void beforeDelete(CcfaMenu e) {
        long children = menus.countByMenuParentIdx(e.getIdx());
        if (children > 0) throw new IllegalStateException("하위 메뉴가 " + children + "건 있어 삭제할 수 없습니다.");
    }

    /** 부모 메뉴 select 와 목록의 부모 이름 표시용. 트리 순서(부모, 순번, IDX). */
    @Transactional(readOnly = true)
    public List<CcfaMenu> allForSelect() {
        requireSuperForGlobalTable();
        return menus.findAllByOrderByMenuParentIdxAscMenuSeqAscIdxAsc();
    }
}
