package com.crosscert.fidoadmin.system.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.system.entity.CcfaOption;
import com.crosscert.fidoadmin.system.entity.CcfaOptions;
import com.crosscert.fidoadmin.system.repository.CcfaOptionRepository;
import com.crosscert.fidoadmin.system.repository.CcfaOptionsRepository;
import com.crosscert.fidoadmin.system.web.OptionSearchForm;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CCFA_OPTION(코드 그룹) CRUD + CCFA_OPTIONS(코드) 인라인 관리. COMPANY_IDX 가 없으므로 SUPER 전용.
 * 코드는 그룹 상세 안에서만 추가·삭제하며 별도 화면이 없다.
 */
@Service
public class OptionService extends CrudService<CcfaOption, Long, OptionSearchForm> {

    private final CcfaOptionsRepository items;

    public OptionService(CcfaOptionRepository repository, AuditLogger audit, CcfaOptionsRepository items) {
        super(repository, audit);
        this.items = items;
    }

    @Override protected Specification<CcfaOption> toSpecification(OptionSearchForm f) {
        return Specs.all(Specs.like("optionName", f.getOptionName()));
    }
    @Override protected String companyIdxAttribute() { return null; }
    @Override protected Long companyIdxOf(CcfaOption e) { return null; }
    @Override protected void setCompanyIdx(CcfaOption e, Long c) {}
    @Override public String idOf(CcfaOption e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "CCFA_OPTION"; }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "optionName"); }

    @Override protected void beforeDelete(CcfaOption e) {
        long count = items.countByOptionIdx(e.getIdx());
        if (count > 0) throw new IllegalStateException("하위 코드 " + count + "건이 있어 삭제할 수 없습니다. 코드를 먼저 삭제하세요.");
    }

    @Transactional(readOnly = true)
    public List<CcfaOptions> items(Long optionIdx) {
        requireSuperForGlobalTable();
        return items.findByOptionIdxOrderByIdxAsc(optionIdx);
    }

    /** 그룹 존재 확인 후 코드를 추가한다. OPTION_IDX 는 폼이 아니라 경로의 그룹으로 강제한다. */
    @Transactional
    public CcfaOptions addItem(Long optionIdx, CcfaOptions item) {
        CcfaOption group = get(optionIdx);
        item.setIdx(null);
        item.setOptionIdx(group.getIdx());
        CcfaOptions saved = items.save(item);
        audit.log(AuditType.CREATE, "CCFA_OPTIONS CREATE " + saved.getIdx());
        return saved;
    }

    /** 경로의 그룹에 속한 코드만 지운다. 다른 그룹의 코드 IDX 를 넣으면 존재하지 않는 것으로 본다. */
    @Transactional
    public void removeItem(Long optionIdx, Long itemIdx) {
        CcfaOption group = get(optionIdx);
        CcfaOptions item = items.findById(itemIdx)
            .filter(i -> group.getIdx().equals(i.getOptionIdx()))
            .orElseThrow(() -> new EntityNotFoundException("CCFA_OPTIONS " + itemIdx));
        items.delete(item);
        audit.log(AuditType.DELETE, "CCFA_OPTIONS DELETE " + itemIdx);
    }
}
