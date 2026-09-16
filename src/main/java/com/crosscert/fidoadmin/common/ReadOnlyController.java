package com.crosscert.fidoadmin.common;

import org.springframework.data.domain.Page;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;

/** 조회 전용 화면: 목록 + 상세. */
public abstract class ReadOnlyController<E, ID, S extends SearchForm> {

    protected abstract CrudService<E, ID, S> service();
    protected abstract String basePath();
    protected abstract String viewDir();
    protected void populateListModel(Model model) {}
    protected void populateDetailModel(E entity, Model model) {}

    @GetMapping
    public String list(@ModelAttribute("search") S search, Model model) {
        Page<E> page = service().search(search, search.toPageable(service().defaultSort()));
        model.addAttribute("page", page);
        model.addAttribute("searchQs", search.toQueryString());
        model.addAttribute("basePath", basePath());
        populateListModel(model);
        return viewDir() + "/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable ID id, Model model) {
        E entity = service().get(id);
        model.addAttribute("item", entity);
        model.addAttribute("basePath", basePath());
        populateDetailModel(entity, model);
        return viewDir() + "/detail";
    }
}
