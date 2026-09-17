package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.system.entity.CcfaFields;
import com.crosscert.fidoadmin.system.entity.CcfaOption;
import com.crosscert.fidoadmin.system.service.FieldService;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/system/fields")
@RequiredArgsConstructor
public class FieldController extends CrudController<CcfaFields, Long, FieldForm, FieldSearchForm> {

    private final FieldService service;

    @Override protected CrudService<CcfaFields, Long, FieldSearchForm> service() { return service; }
    @Override protected String basePath() { return "/system/fields"; }
    @Override protected String viewDir() { return "system/fields"; }
    @Override protected FieldSearchForm newSearchForm() { return new FieldSearchForm(); }
    @Override protected FieldForm newForm() { return new FieldForm(); }
    @Override protected FieldForm toForm(CcfaFields e) { return FieldForm.from(e); }
    @Override protected CcfaFields toEntity(FieldForm f) { return f.toNewEntity(); }
    @Override protected void applyForm(FieldForm f, CcfaFields e) { f.applyTo(e); }

    @Override protected void populateFormModel(Model model) { model.addAttribute("options", service.options()); }
    @Override protected void populateListModel(Model model) { model.addAttribute("optionNames", optionNames()); }
    @Override protected void populateDetailModel(CcfaFields e, Model model) { model.addAttribute("optionNames", optionNames()); }

    private Map<Long, String> optionNames() {
        Map<Long, String> names = new LinkedHashMap<>();
        for (CcfaOption o : service.options()) names.put(o.getIdx(), o.getOptionName());
        return names;
    }
}
