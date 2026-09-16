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

    /**
     * 목록 행 → 출력용 DTO. 설계 2.2 에 따라 엔티티를 뷰에 직접 노출하지 않는다.
     * 민감 컬럼이 있는 테이블(USERINFO.PUBKEY/CERTIFICATE, CCFA_MANAGER.USER_PW,
     * AWS_INFO.AMZ_TOKEN)의 화면은 <b>반드시</b> 이 훅을 구현해 해당 컬럼을 빼거나
     * 앞 16자만 남긴다. CLOB 컬럼도 목록에서는 제외한다.
     */
    protected Object toListView(E entity) { return entity; }

    /** 상세 → 출력용 DTO. 규칙은 {@link #toListView} 와 같다. */
    protected Object toDetailView(E entity) { return entity; }

    @GetMapping
    public String list(@ModelAttribute("search") S search, Model model) {
        Page<E> page = service().search(search, search.toPageable(service().defaultSort(), service().sortableProperties()));
        model.addAttribute("page", page.map(this::toListView));
        model.addAttribute("searchQs", search.toQueryString());
        model.addAttribute("basePath", basePath());
        populateListModel(model);
        return viewDir() + "/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable ID id, Model model) {
        E entity = service().get(id);
        model.addAttribute("item", toDetailView(entity));
        model.addAttribute("basePath", basePath());
        populateDetailModel(entity, model);
        return viewDir() + "/detail";
    }
}
