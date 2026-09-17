package com.crosscert.fidoadmin.system.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.system.entity.CcfaFields;
import com.crosscert.fidoadmin.system.entity.CcfaOption;
import com.crosscert.fidoadmin.system.repository.CcfaFieldsRepository;
import com.crosscert.fidoadmin.system.repository.CcfaOptionRepository;
import com.crosscert.fidoadmin.system.web.FieldSearchForm;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CCFA_FIELDS — 화면 필드 메타 정의. COMPANY_IDX 가 없으므로 SUPER 전용. */
@Service
public class FieldService extends CrudService<CcfaFields, Long, FieldSearchForm> {

    private final CcfaOptionRepository options;

    public FieldService(CcfaFieldsRepository repository, AuditLogger audit, CcfaOptionRepository options) {
        super(repository, audit);
        this.options = options;
    }

    @Override protected Specification<CcfaFields> toSpecification(FieldSearchForm f) {
        return Specs.all(Specs.like("fieldTable", f.getFieldTable()), Specs.like("fieldName", f.getFieldName()));
    }
    @Override protected String companyIdxAttribute() { return null; }
    @Override protected Long companyIdxOf(CcfaFields e) { return null; }
    @Override protected void setCompanyIdx(CcfaFields e, Long c) {}
    @Override public String idOf(CcfaFields e) { return String.valueOf(e.getIdx()); }
    @Override protected String tableName() { return "CCFA_FIELDS"; }
    @Override public Sort defaultSort() { return Sort.by(Sort.Order.asc("fieldTable"), Sort.Order.asc("idx")); }
    @Override public Set<String> sortableProperties() { return Set.of("idx", "fieldTable", "fieldName"); }

    @Override protected void applyDefaults(CcfaFields e) {
        if (e.getPk() == null) e.setPk(0L);
        if (e.getFk() == null) e.setFk(0L);
        if (e.getEditable() == null) e.setEditable(0L);
    }

    /** 폼의 코드 그룹 select 와 목록의 그룹 이름 표시용. */
    @Transactional(readOnly = true)
    public List<CcfaOption> options() {
        requireSuperForGlobalTable();
        return options.findAll(Sort.by("idx"));
    }
}
