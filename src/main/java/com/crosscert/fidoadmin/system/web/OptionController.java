package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.system.entity.CcfaOption;
import com.crosscert.fidoadmin.system.service.OptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/system/options")
@RequiredArgsConstructor
public class OptionController extends CrudController<CcfaOption, Long, OptionForm, OptionSearchForm> {

    private final OptionService service;

    @Override protected CrudService<CcfaOption, Long, OptionSearchForm> service() { return service; }
    @Override protected String basePath() { return "/system/options"; }
    @Override protected String viewDir() { return "system/options"; }
    @Override protected OptionSearchForm newSearchForm() { return new OptionSearchForm(); }
    @Override protected OptionForm newForm() { return new OptionForm(); }
    @Override protected OptionForm toForm(CcfaOption e) { return OptionForm.from(e); }
    @Override protected CcfaOption toEntity(OptionForm f) { return f.toNewEntity(); }
    @Override protected void applyForm(OptionForm f, CcfaOption e) { f.applyTo(e); }

    @Override protected void populateDetailModel(CcfaOption e, Model model) {
        model.addAttribute("items", service.items(e.getIdx()));
        if (!model.containsAttribute("itemForm")) model.addAttribute("itemForm", new OptionItemForm());
    }

    /** 코드 인라인 추가. 검증 실패는 상세로 돌아가 플래시로 알린다(상세 화면을 폼처럼 다시 그리지 않는다). */
    @PostMapping("/{id}/items")
    public String addItem(@PathVariable Long id, @Valid @ModelAttribute("itemForm") OptionItemForm itemForm,
                          BindingResult binding, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            redirect.addFlashAttribute("flashError", "코드 값은 필수입니다(128바이트 이내).");
            return "redirect:/system/options/" + id;
        }
        service.addItem(id, itemForm.toNewEntity());
        redirect.addFlashAttribute("flashSuccess", "코드가 추가되었습니다.");
        return "redirect:/system/options/" + id;
    }

    @PostMapping("/{id}/items/{itemIdx}/delete")
    public String removeItem(@PathVariable Long id, @PathVariable Long itemIdx, RedirectAttributes redirect) {
        service.removeItem(id, itemIdx);
        redirect.addFlashAttribute("flashSuccess", "코드가 삭제되었습니다.");
        return "redirect:/system/options/" + id;
    }
}
