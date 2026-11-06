package com.crosscert.fidoadmin.common

import com.crosscert.fidoadmin.audit.AuditView
import jakarta.validation.Valid
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/**
 * 목록/상세/등록/수정/삭제 공통 컨트롤러. 하위 클래스는 @Controller @RequestMapping("/기본경로") 를 붙인다.
 * 뷰: {viewDir}/list, {viewDir}/detail, {viewDir}/form
 */
abstract class CrudController<E : Any, ID : Any, F, S : SearchForm> {

    protected abstract fun service(): CrudService<E, ID, S>
    /** "/companies" 처럼 슬래시로 시작하는 기본 경로. */
    protected abstract fun basePath(): String
    /** "company/company" 처럼 templates/ 아래 디렉터리. */
    protected abstract fun viewDir(): String
    protected abstract fun newForm(): F
    protected abstract fun toForm(entity: E): F
    /**
     * 폼 → 신규 엔티티. 반드시 새 인스턴스를 만들고, 식별자(IDX 등)는 절대 폼 값으로 채우지 않는다.
     * 식별자가 채워진 엔티티를 넘기면 repository.save() 가 INSERT 가 아니라 MERGE 로 동작해
     * 기존 행을 덮어쓸 수 있다(할당형 PK 테이블에서 특히 위험).
     */
    protected abstract fun toEntity(form: F): E
    protected abstract fun applyForm(form: F, entity: E)

    /**
     * 목록 행 → 출력용 DTO. 엔티티를 뷰에 직접 노출하지 않는다.
     * 민감 컬럼이 있는 테이블(CCFA_MANAGER.USER_PW, USERINFO.PUBKEY/CERTIFICATE,
     * AWS_INFO.AMZ_TOKEN)의 화면은 **반드시** 이 훅을 구현해 해당 컬럼을 빼거나
     * 앞 16자만 남긴다. CLOB 컬럼도 목록에서는 제외한다.
     * 민감 컬럼이 없는 테이블은 기본 구현(엔티티 그대로)을 쓴다.
     */
    protected open fun toListView(entity: E): Any? = entity

    /** 상세 → 출력용 DTO. 규칙은 [toListView] 와 같다. */
    protected open fun toDetailView(entity: E): Any? = entity
    /** 폼 화면에 필요한 선택 목록 등. */
    protected open fun populateFormModel(model: Model) {}
    protected open fun populateListModel(model: Model) {}
    protected open fun populateDetailModel(entity: E, model: Model) {}

    /**
     * Bean Validation 으로 표현하기 어려운 검증(등록 시에만 필수, 두 필드 비교, JSON 형식 등).
     * binding.rejectValue / reject 로 오류를 넣으면 폼을 다시 그린다.
     */
    protected open fun validate(form: F, isNew: Boolean, binding: BindingResult) {}

    /**
     * 식별자가 필요한 검증(수정 시 자기 자신을 제외하는 중복 검사 등).
     * 등록이면 `id` 는 null 이다. 기본 구현은 위 훅으로 넘기므로 기존 컨트롤러는 영향이 없다.
     */
    protected open fun validate(form: F, id: ID?, binding: BindingResult) { validate(form, id == null, binding) }

    @GetMapping
    open fun list(@ModelAttribute("search") search: S, model: Model): String {
        val page = service().search(search, search.toPageable(service().defaultSort(), service().sortableProperties()))
        model.addAttribute("page", page.map { toListView(it) })
        model.addAttribute("searchQs", search.toQueryString())
        model.addAttribute("basePath", basePath())
        populateListModel(model)
        return viewDir() + "/list"
    }

    @GetMapping("/new")
    open fun createForm(model: Model): String {
        model.addAttribute("form", newForm())
        model.addAttribute("isNew", true)
        model.addAttribute("basePath", basePath())
        populateFormModel(model)
        return viewDir() + "/form"
    }

    /** 등록 성공 메시지. 등록이 딸린 효과를 알려야 하는 화면이 바꾼다. */
    protected open fun createdMessage(saved: E): String = "등록되었습니다."

    @PostMapping
    open fun create(@Valid @ModelAttribute("form") form: F, binding: BindingResult, model: Model,
                    redirect: RedirectAttributes): String {
        validate(form, null as ID?, binding)
        if (binding.hasErrors()) return backToForm(model, true)
        try {
            val saved = service().create(toEntity(form))
            redirect.addFlashAttribute("flashSuccess", createdMessage(saved))
            return "redirect:" + basePath() + "/" + service().idOf(saved)
        } catch (e: DataIntegrityViolationException) {
            binding.reject("duplicate", "이미 존재하는 값이거나 제약 조건에 어긋납니다.")
            return backToForm(model, true)
        } catch (e: IllegalStateException) {
            // 서비스가 업무 규칙으로 거부한 경우. update() 와 같이 500 이 아니라 사유를 폼에 돌려준다.
            binding.reject("rejected", null, e.message)
            return backToForm(model, true)
        }
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

    @GetMapping("/{id}/edit")
    open fun editForm(@PathVariable id: ID, model: Model): String {
        val entity = service().get(id)
        model.addAttribute("form", toForm(entity))
        model.addAttribute("isNew", false)
        model.addAttribute("id", id)
        model.addAttribute("basePath", basePath())
        populateFormModel(model)
        return viewDir() + "/form"
    }

    @PostMapping("/{id}")
    open fun update(@PathVariable id: ID, @Valid @ModelAttribute("form") form: F, binding: BindingResult,
                    model: Model, redirect: RedirectAttributes): String {
        model.addAttribute("id", id)
        validate(form, id, binding)
        if (binding.hasErrors()) return backToForm(model, false)
        try {
            service().update(id) { entity -> applyForm(form, entity) }
            redirect.addFlashAttribute("flashSuccess", "수정되었습니다.")
            return "redirect:" + basePath() + "/" + id
        } catch (e: DataIntegrityViolationException) {
            binding.reject("duplicate", "이미 존재하는 값이거나 제약 조건에 어긋납니다.")
            return backToForm(model, false)
        } catch (e: IllegalStateException) {
            // 서비스가 업무 규칙으로 거부한 경우(예: 가입 신청 상태 행의 상태 변경).
            // 500 이 아니라 사유를 그대로 폼에 돌려준다. delete() 의 IllegalStateException 처리와 같은 성격이다.
            binding.reject("rejected", null, e.message)
            return backToForm(model, false)
        }
    }

    @PostMapping("/{id}/delete")
    open fun delete(@PathVariable id: ID, redirect: RedirectAttributes): String {
        try {
            service().delete(id)
            redirect.addFlashAttribute("flashSuccess", "삭제되었습니다.")
            return "redirect:" + basePath()
        } catch (e: IllegalStateException) {
            redirect.addFlashAttribute("flashError", e.message)
            return "redirect:" + basePath() + "/" + id
        }
    }

    private fun backToForm(model: Model, isNew: Boolean): String {
        model.addAttribute("isNew", isNew)
        model.addAttribute("basePath", basePath())
        populateFormModel(model)
        return viewDir() + "/form"
    }
}
