package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.system.entity.CcfaSystemProp;
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId;
import com.crosscert.fidoadmin.system.service.SystemPropService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/system/props")
@RequiredArgsConstructor
public class SystemPropController extends CrudController<CcfaSystemProp, CcfaSystemPropId, SystemPropForm, SystemPropSearchForm> {

    private final SystemPropService service;
    private final CompanyLookup companies;
    private final TenantContext tenant;

    @Override protected CrudService<CcfaSystemProp, CcfaSystemPropId, SystemPropSearchForm> service() { return service; }
    @Override protected String basePath() { return "/system/props"; }
    @Override protected String viewDir() { return "system/props"; }
    @Override protected SystemPropSearchForm newSearchForm() { return new SystemPropSearchForm(); }
    @Override protected SystemPropForm newForm() { return new SystemPropForm(); }
    @Override protected SystemPropForm toForm(CcfaSystemProp e) { return SystemPropForm.from(e); }
    /** 복합키(PROP_KEY + COMPANY_IDX)의 COMPANY_IDX 를 유효 테넌트로 채운다. 고객사 select 가 사라졌으므로 폼에는 값이 없다. */
    @Override protected CcfaSystemProp toEntity(SystemPropForm f) { return f.toNewEntity(tenant.companyIdx()); }
    @Override protected void applyForm(SystemPropForm f, CcfaSystemProp e) { f.applyTo(e); }
    @Override protected Object toListView(CcfaSystemProp e) { return SystemPropRow.of(e); }
    @Override protected Object toDetailView(CcfaSystemProp e) { return SystemPropRow.of(e); }

    /**
     * PROP_KEY 는 CRUD 경로의 {id} 세그먼트 조합으로 그대로 쓰인다("/system/props/{key}@{companyIdx}" 등).
     * "new" 는 등록 폼 경로("/new")와 겹치고, 점으로만 된 값("." , "..")은 경로 세그먼트로서
     * 특수한 의미를 가져 상세/수정/삭제 링크가 만들어지지 않는다. 문자 집합 자체는
     * @Pattern 으로 이미 제한했으니 여기서는 예약된 값만 추가로 막는다.
     */
    @Override protected void validate(SystemPropForm form, boolean isNew, BindingResult binding) {
        if (isNew && form.getPropKey() != null) {
            String key = form.getPropKey();
            if (key.equalsIgnoreCase("new") || key.matches("\\.+")) {
                binding.rejectValue("propKey", "reserved", "키로 쓸 수 없는 값입니다: " + key);
            }
        }
    }

    @Override protected void populateDetailModel(CcfaSystemProp e, Model model) {
        model.addAttribute("companyName", companies.name(e.getId().getCompanyIdx()));
    }
}
