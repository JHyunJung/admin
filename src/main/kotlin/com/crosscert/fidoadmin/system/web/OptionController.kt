package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.common.CrudController
import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.system.entity.CcfaOption
import com.crosscert.fidoadmin.system.service.OptionService
import jakarta.validation.Valid
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
@RequestMapping("/system/options")
class OptionController(
    private val service: OptionService,
) : CrudController<CcfaOption, Long, OptionForm, OptionSearchForm>() {

    override fun service(): CrudService<CcfaOption, Long, OptionSearchForm> = service
    override fun basePath(): String = "/system/options"
    override fun viewDir(): String = "system/options"
    override fun newForm(): OptionForm = OptionForm()
    override fun toForm(e: CcfaOption): OptionForm = OptionForm.from(e)
    override fun toEntity(f: OptionForm): CcfaOption = f.toNewEntity()
    override fun applyForm(f: OptionForm, e: CcfaOption) { f.applyTo(e) }

    override fun populateDetailModel(e: CcfaOption, model: Model) {
        model.addAttribute("items", service.items(e.idx))
        if (!model.containsAttribute("itemForm")) model.addAttribute("itemForm", OptionItemForm())
    }

    /** 코드 인라인 추가. 검증 실패는 상세로 돌아가 플래시로 알린다(상세 화면을 폼처럼 다시 그리지 않는다). */
    @PostMapping("/{id}/items")
    fun addItem(
        @PathVariable id: Long,
        @Valid @ModelAttribute("itemForm") itemForm: OptionItemForm,
        binding: BindingResult,
        redirect: RedirectAttributes,
    ): String {
        if (binding.hasErrors()) {
            redirect.addFlashAttribute("flashError", "코드 값은 필수입니다(128바이트 이내).")
            return "redirect:/system/options/$id"
        }
        service.addItem(id, itemForm.toNewEntity())
        redirect.addFlashAttribute("flashSuccess", "코드가 추가되었습니다.")
        return "redirect:/system/options/$id"
    }

    @PostMapping("/{id}/items/{itemIdx}/delete")
    fun removeItem(@PathVariable id: Long, @PathVariable itemIdx: Long, redirect: RedirectAttributes): String {
        service.removeItem(id, itemIdx)
        redirect.addFlashAttribute("flashSuccess", "코드가 삭제되었습니다.")
        return "redirect:/system/options/$id"
    }
}
