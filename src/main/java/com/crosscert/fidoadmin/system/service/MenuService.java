package com.crosscert.fidoadmin.system.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.system.entity.CcfaMenu;
import com.crosscert.fidoadmin.system.repository.CcfaMenuRepository;
import com.crosscert.fidoadmin.system.web.MenuSearchForm;
import java.util.HashSet;
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

    /** idx 의 모든 하위(자손) 메뉴 IDX. 각 메뉴에서 부모 체인을 idx 까지 걷어 올라가며, 방문 집합으로
     *  기존 데이터에 순환이 있어도 무한루프 없이 종료한다. */
    @Transactional(readOnly = true)
    public Set<Long> descendantIdxs(Long idx) {
        List<CcfaMenu> all = allForSelect();
        Set<Long> descendants = new HashSet<>();
        for (CcfaMenu m : all) {
            if (chainReaches(m, idx, all)) descendants.add(m.getIdx());
        }
        return descendants;
    }

    private boolean chainReaches(CcfaMenu m, Long idx, List<CcfaMenu> all) {
        Set<Long> visited = new HashSet<>();
        Long parent = m.getMenuParentIdx();
        while (parent != null && !parent.equals(0L) && visited.add(parent)) {
            if (parent.equals(idx)) return true;
            CcfaMenu next = findByIdx(all, parent);
            if (next == null) break;
            parent = next.getMenuParentIdx();
        }
        return false;
    }

    private CcfaMenu findByIdx(List<CcfaMenu> all, Long idx) {
        for (CcfaMenu m : all) if (m.getIdx().equals(idx)) return m;
        return null;
    }

    /** candidateParent 가 idx 자신이거나 idx 의 하위 메뉴이면 true(순환). */
    public boolean isSelfOrDescendant(Long candidateParent, Long idx) {
        return candidateParent != null && idx != null
            && (candidateParent.equals(idx) || descendantIdxs(idx).contains(candidateParent));
    }

    /** 부모 선택지: editingIdx==null(등록)이면 전체, 아니면 자기 자신과 모든 하위를 제외. */
    @Transactional(readOnly = true)
    public List<CcfaMenu> selectableParentsFor(Long editingIdx) {
        List<CcfaMenu> all = allForSelect();
        if (editingIdx == null) return all;
        Set<Long> excluded = descendantIdxs(editingIdx);
        return all.stream()
            .filter(m -> !m.getIdx().equals(editingIdx) && !excluded.contains(m.getIdx()))
            .toList();
    }

    /** parentIdx 가 유효한 부모 후보인지: 0(최상위)이거나 실제 존재하는 메뉴여야 한다. */
    @Transactional(readOnly = true)
    public boolean parentExists(Long parentIdx) {
        return parentIdx != null && (parentIdx.equals(0L) || menus.existsById(parentIdx));
    }
}
