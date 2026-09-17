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
import org.springframework.validation.BindingResult;
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

    /**
     * 등록·수정 공통: 코드 그룹으로 지정한 IDX 가 비어 있거나 실제 존재하는 CCFA_OPTION 이어야 한다.
     * 이 검증은 applyForm(엔티티 반영) 이전에 돌기 때문에, 존재하지 않는(허공의) 코드 그룹은
     * DB 에 쓰이기 전에 걸러진다.
     */
    @Override protected void validate(FieldForm form, boolean isNew, BindingResult binding) {
        if (!service.optionExists(form.getOptionIdx())) {
            binding.rejectValue("optionIdx", "unknown", "존재하지 않는 코드 그룹입니다.");
        }
    }

    @Override protected void populateFormModel(Model model) { model.addAttribute("options", service.options()); }
    @Override protected void populateListModel(Model model) { model.addAttribute("optionNames", optionNames()); }
    @Override protected void populateDetailModel(CcfaFields e, Model model) { model.addAttribute("optionNames", optionNames()); }

    private Map<Long, String> optionNames() {
        Map<Long, String> names = new LinkedHashMap<>();
        for (CcfaOption o : service.options()) names.put(o.getIdx(), o.getOptionName());
        return names;
    }
}
