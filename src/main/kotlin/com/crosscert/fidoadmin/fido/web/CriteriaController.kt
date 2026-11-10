package com.crosscert.fidoadmin.fido.web

import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.common.ReadOnlyController
import com.crosscert.fidoadmin.fido.entity.Criteria
import com.crosscert.fidoadmin.fido.service.CriteriaMetadataException
import com.crosscert.fidoadmin.fido.service.CriteriaQueryService
import jakarta.validation.Valid
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/**
 * AAID(정책) 조회와 고객사별 활성/비활성 토글.
 *
 * 기준 데이터(CRITERIA) 자체는 전역이지만 토글 상태는 고객사별이라
 * COMPANY 역할도 쓴다. 고객사 경계는 서비스가 유효 테넌트로 건다.
 *
 * 등록(/new, POST)은 SUPER 전용이다. 수정·삭제 경로가 열리지 않도록 기반을
 * CrudController 로 바꾸지 않고 두 핸들러만 둔다.
 */
@Controller
@RequestMapping("/criteria")
class CriteriaController(
    private val service: CriteriaQueryService,
) : ReadOnlyController<Criteria, Long, CriteriaSearchForm>() {

    override fun service(): CrudService<Criteria, Long, CriteriaSearchForm> = service
    override fun basePath(): String = "/criteria"
    override fun viewDir(): String = "fido/criteria"
    override fun toListView(entity: Criteria): Any = CriteriaRow.of(entity)
    override fun toDetailView(entity: Criteria): Any = CriteriaView.of(entity)

    /** 목록의 상태 뱃지가 쓴다. 현재 고객사에서 꺼 둔 AAID 집합. */
    override fun populateListModel(model: Model) {
        model.addAttribute("disabledAaids", service.disabledAaids())
    }

    @GetMapping("/new")
    fun createForm(@AuthenticationPrincipal me: ManagerUserDetails?, model: Model): String {
        requireSuper(me)
        model.addAttribute("form", CriteriaMetadataForm())
        return viewDir() + "/form"
    }

    @PostMapping
    fun create(
        @AuthenticationPrincipal me: ManagerUserDetails?,
        @Valid @ModelAttribute("form") form: CriteriaMetadataForm,
        binding: BindingResult,
        redirect: RedirectAttributes,
    ): String {
        requireSuper(me)
        if (binding.hasErrors()) return viewDir() + "/form"
        try {
            val saved = service.create(form.jsondata)
            redirect.addFlashAttribute("flashSuccess", "등록되었습니다. 모든 고객사에서 비활성 상태로 시작합니다.")
            return "redirect:" + basePath() + "/" + saved.idx
        } catch (e: CriteriaMetadataException) {
            // CriteriaMetadataException 은 메시지를 필수로 받으므로 null 이 아니다.
            binding.rejectValue("jsondata", "invalid", e.message.orEmpty())
            return viewDir() + "/form"
        }
    }

    /**
     * 고객사별 AAID 활성/비활성 토글.
     *
     * 검색·페이지 상태를 리다이렉트에 다시 실어 준다. 토글 후 1페이지로
     * 튕기면 목록을 훑으며 여러 건을 끄고 켤 때 매번 찾아 들어가야 한다.
     */
    @PostMapping("/{id}/status")
    fun changeStatus(
        @PathVariable("id") id: Long,
        @RequestParam("enabled") enabled: Boolean,
        @ModelAttribute("search") search: CriteriaSearchForm,
        redirect: RedirectAttributes,
    ): String {
        val changed = service.changeStatus(id, enabled)
        redirect.addFlashAttribute(
            "statusMessage",
            if (changed) "상태를 변경했습니다." else "이미 해당 상태입니다.",
        )
        return backToList(search, redirect)
    }

    /** 현재 고객사의 AAID 전체 활성/비활성. 검색·페이지 상태는 단건 토글과 같이 유지한다. */
    @PostMapping("/status")
    fun changeStatusAll(
        @RequestParam("enabled") enabled: Boolean,
        @ModelAttribute("search") search: CriteriaSearchForm,
        redirect: RedirectAttributes,
    ): String {
        val changed = service.changeStatusAll(enabled)
        redirect.addFlashAttribute(
            "statusMessage",
            if (changed == 0) {
                "이미 모두 해당 상태입니다."
            } else {
                (if (enabled) "전체 활성: " else "전체 비활성: ") + changed + "건을 변경했습니다."
            },
        )
        return backToList(search, redirect)
    }

    /** 검색·페이지 상태(aaid·page·size 순)를 리다이렉트에 실어 목록으로 돌려보낸다. 단건·전체 토글이 같이 쓴다. */
    private fun backToList(search: CriteriaSearchForm, redirect: RedirectAttributes): String {
        redirect.addAttribute("aaid", search.aaid)
        redirect.addAttribute("page", search.page)
        redirect.addAttribute("size", search.size)
        return "redirect:/criteria"
    }

    private companion object {
        /** 서비스도 막지만, 폼 화면(GET)은 서비스를 거치지 않으므로 여기서도 막는다. */
        fun requireSuper(me: ManagerUserDetails?) {
            if (me == null || !me.isSuper) {
                throw AccessDeniedException("AAID 정책 등록은 최고 관리자 전용입니다")
            }
        }
    }
}
