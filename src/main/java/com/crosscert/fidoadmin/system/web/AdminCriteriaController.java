package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.JsonPretty;
import com.crosscert.fidoadmin.system.entity.CcfaCriteria;
import com.crosscert.fidoadmin.system.service.AdminCriteriaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/system/criteria")
@RequiredArgsConstructor
public class AdminCriteriaController extends CrudController<CcfaCriteria, Long, AdminCriteriaForm, AdminCriteriaSearchForm> {

    private final AdminCriteriaService service;

    @Override protected CrudService<CcfaCriteria, Long, AdminCriteriaSearchForm> service() { return service; }
    @Override protected String basePath() { return "/system/criteria"; }
    @Override protected String viewDir() { return "system/criteria"; }
    @Override protected AdminCriteriaSearchForm newSearchForm() { return new AdminCriteriaSearchForm(); }
    @Override protected AdminCriteriaForm newForm() { return new AdminCriteriaForm(); }
    @Override protected AdminCriteriaForm toForm(CcfaCriteria e) { return AdminCriteriaForm.from(e); }
    @Override protected CcfaCriteria toEntity(AdminCriteriaForm f) { return f.toNewEntity(); }
    @Override protected void applyForm(AdminCriteriaForm f, CcfaCriteria e) { f.applyTo(e); }
    @Override protected Object toListView(CcfaCriteria e) { return AdminCriteriaRow.of(e); }
    @Override protected Object toDetailView(CcfaCriteria e) { return AdminCriteriaView.of(e); }

    @Override protected void validate(AdminCriteriaForm form, boolean isNew, BindingResult binding) {
        String json = form.getJsondata();
        if (json != null && !json.isBlank() && !JsonPretty.isValidJson(json)) {
            binding.rejectValue("jsondata", "json", "올바른 JSON(객체 또는 배열)이어야 합니다.");
        }
    }
}
