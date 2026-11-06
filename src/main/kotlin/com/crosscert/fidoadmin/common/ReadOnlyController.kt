package com.crosscert.fidoadmin.common

import com.crosscert.fidoadmin.audit.AuditView
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable

/** 조회 전용 화면: 목록 + 상세. */
abstract class ReadOnlyController<E : Any, ID : Any, S : SearchForm> {

    protected abstract fun service(): CrudService<E, ID, S>
    protected abstract fun basePath(): String
    protected abstract fun viewDir(): String
    protected open fun populateListModel(model: Model) {}
    protected open fun populateDetailModel(entity: E, model: Model) {}

    /**
     * 목록 행 → 출력용 DTO. 엔티티를 뷰에 직접 노출하지 않는다.
     * 민감 컬럼이 있는 테이블(USERINFO.PUBKEY/CERTIFICATE, CCFA_MANAGER.USER_PW,
     * AWS_INFO.AMZ_TOKEN)의 화면은 **반드시** 이 훅을 구현해 해당 컬럼을 빼거나
     * 앞 16자만 남긴다. CLOB 컬럼도 목록에서는 제외한다.
     */
    protected open fun toListView(entity: E): Any? = entity

    /** 상세 → 출력용 DTO. 규칙은 [toListView] 와 같다. */
    protected open fun toDetailView(entity: E): Any? = entity

    @GetMapping
    open fun list(@ModelAttribute("search") search: S, model: Model): String {
        val page = service().search(search, search.toPageable(service().defaultSort(), service().sortableProperties()))
        model.addAttribute("page", page.map { toListView(it) })
        model.addAttribute("searchQs", search.toQueryString())
        model.addAttribute("basePath", basePath())
        populateListModel(model)
        return viewDir() + "/list"
    }

    @GetMapping("/{id}")
    open fun detail(@PathVariable id: ID, model: Model): String {
        val entity = service().get(id)
        model.addAttribute("item", toDetailView(entity))
        model.addAttribute("basePath", basePath())
        populateDetailModel(entity, model)
        // 어느 레코드를 보여 줬는지 남긴다. AuditReadInterceptor 가 응답 뒤 DATA_VIEW 로 기록한다.
        AuditView.add(model, service().tableNameForAudit(), service().idOf(entity))
        return viewDir() + "/detail"
    }
}
