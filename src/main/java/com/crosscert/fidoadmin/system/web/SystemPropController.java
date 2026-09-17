package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
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

    @Override protected CrudService<CcfaSystemProp, CcfaSystemPropId, SystemPropSearchForm> service() { return service; }
    @Override protected String basePath() { return "/system/props"; }
    @Override protected String viewDir() { return "system/props"; }
    @Override protected SystemPropSearchForm newSearchForm() { return new SystemPropSearchForm(); }
    @Override protected SystemPropForm newForm() { return new SystemPropForm(); }
    @Override protected SystemPropForm toForm(CcfaSystemProp e) { return SystemPropForm.from(e); }
    @Override protected CcfaSystemProp toEntity(SystemPropForm f) { return f.toNewEntity(); }
    @Override protected void applyForm(SystemPropForm f, CcfaSystemProp e) { f.applyTo(e); }
    @Override protected Object toListView(CcfaSystemProp e) { return SystemPropRow.of(e); }
    @Override protected Object toDetailView(CcfaSystemProp e) { return SystemPropRow.of(e); }

    /** 경로 조각으로 쓰이는 키라 '/' 는 받지 않는다. */
    @Override protected void validate(SystemPropForm form, boolean isNew, BindingResult binding) {
        if (isNew && form.getPropKey() != null && form.getPropKey().contains("/")) {
            binding.rejectValue("propKey", "path", "키에 '/' 는 쓸 수 없습니다.");
        }
    }

    @Override protected void populateListModel(Model model) {
        model.addAttribute("companies", companies.all());
        model.addAttribute("companyNames", companies.names());
    }
    @Override protected void populateFormModel(Model model) {
        model.addAttribute("companies", companies.all());
        model.addAttribute("companyNames", companies.names());
    }
    @Override protected void populateDetailModel(CcfaSystemProp e, Model model) {
        model.addAttribute("companyName", companies.name(e.getId().getCompanyIdx()));
    }
}
