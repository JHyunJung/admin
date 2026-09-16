package com.crosscert.fidoadmin.common;

import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 목록/상세/등록/수정/삭제 공통 컨트롤러. 하위 클래스는 @Controller @RequestMapping("/기본경로") 를 붙인다.
 * 뷰: {viewDir}/list, {viewDir}/detail, {viewDir}/form
 */
public abstract class CrudController<E, ID, F, S extends SearchForm> {

    protected abstract CrudService<E, ID, S> service();
    /** "/companies" 처럼 슬래시로 시작하는 기본 경로. */
    protected abstract String basePath();
    /** "company/company" 처럼 templates/ 아래 디렉터리. */
    protected abstract String viewDir();
    protected abstract S newSearchForm();
    protected abstract F newForm();
    protected abstract F toForm(E entity);
    /**
     * 폼 → 신규 엔티티. 반드시 새 인스턴스를 만들고, 식별자(IDX 등)는 절대 폼 값으로 채우지 않는다.
     * 식별자가 채워진 엔티티를 넘기면 repository.save() 가 INSERT 가 아니라 MERGE 로 동작해
     * 기존 행을 덮어쓸 수 있다(할당형 PK 테이블에서 특히 위험).
     */
    protected abstract E toEntity(F form);
    protected abstract void applyForm(F form, E entity);
    /** 폼 화면에 필요한 선택 목록 등. */
    protected void populateFormModel(Model model) {}
    protected void populateListModel(Model model) {}
    protected void populateDetailModel(E entity, Model model) {}

    @GetMapping
    public String list(@ModelAttribute("search") S search, Model model) {
        Page<E> page = service().search(search, search.toPageable(service().defaultSort(), service().sortableProperties()));
        model.addAttribute("page", page);
        model.addAttribute("searchQs", search.toQueryString());
        model.addAttribute("basePath", basePath());
        populateListModel(model);
        return viewDir() + "/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("form", newForm());
        model.addAttribute("isNew", true);
        model.addAttribute("basePath", basePath());
        populateFormModel(model);
        return viewDir() + "/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") F form, BindingResult binding, Model model,
                         RedirectAttributes redirect) {
        if (binding.hasErrors()) return backToForm(model, true);
        try {
            E saved = service().create(toEntity(form));
            redirect.addFlashAttribute("flashSuccess", "등록되었습니다.");
            return "redirect:" + basePath() + "/" + service().idOf(saved);
        } catch (DataIntegrityViolationException e) {
            binding.reject("duplicate", "이미 존재하는 값이거나 제약 조건에 어긋납니다.");
            return backToForm(model, true);
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable ID id, Model model) {
        E entity = service().get(id);
        model.addAttribute("item", entity);
        model.addAttribute("basePath", basePath());
        populateDetailModel(entity, model);
        return viewDir() + "/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable ID id, Model model) {
        E entity = service().get(id);
        model.addAttribute("form", toForm(entity));
        model.addAttribute("isNew", false);
        model.addAttribute("id", id);
        model.addAttribute("basePath", basePath());
        populateFormModel(model);
        return viewDir() + "/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable ID id, @Valid @ModelAttribute("form") F form, BindingResult binding,
                         Model model, RedirectAttributes redirect) {
        model.addAttribute("id", id);
        if (binding.hasErrors()) return backToForm(model, false);
        try {
            service().update(id, entity -> applyForm(form, entity));
            redirect.addFlashAttribute("flashSuccess", "수정되었습니다.");
            return "redirect:" + basePath() + "/" + id;
        } catch (DataIntegrityViolationException e) {
            binding.reject("duplicate", "이미 존재하는 값이거나 제약 조건에 어긋납니다.");
            return backToForm(model, false);
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable ID id, RedirectAttributes redirect) {
        try {
            service().delete(id);
            redirect.addFlashAttribute("flashSuccess", "삭제되었습니다.");
            return "redirect:" + basePath();
        } catch (IllegalStateException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
            return "redirect:" + basePath() + "/" + id;
        }
    }

    private String backToForm(Model model, boolean isNew) {
        model.addAttribute("isNew", isNew);
        model.addAttribute("basePath", basePath());
        populateFormModel(model);
        return viewDir() + "/form";
    }
}
