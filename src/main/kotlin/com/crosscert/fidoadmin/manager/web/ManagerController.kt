package com.crosscert.fidoadmin.manager.web

import com.crosscert.fidoadmin.common.CrudController
import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.common.PasswordPolicy
import com.crosscert.fidoadmin.company.service.CompanyLookup
import com.crosscert.fidoadmin.manager.entity.CcfaManager
import com.crosscert.fidoadmin.manager.service.ManagerService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
@RequestMapping("/managers")
class ManagerController(
    private val service: ManagerService,
    private val companies: CompanyLookup,
    private val encoder: PasswordEncoder,
) : CrudController<CcfaManager, Long, ManagerForm, ManagerSearchForm>() {

    override fun service(): CrudService<CcfaManager, Long, ManagerSearchForm> = service
    override fun basePath(): String = "/managers"
    override fun viewDir(): String = "manager/manager"
    override fun newForm(): ManagerForm = ManagerForm()
    override fun toForm(e: CcfaManager): ManagerForm = ManagerForm.from(e)

    override fun toEntity(f: ManagerForm): CcfaManager {
        val m = CcfaManager()
        f.applyTo(m)
        m.userPw = encoder.encode(f.password)
        return m
    }

    /** 수정: 비밀번호를 입력했을 때만 바꾼다. */
    override fun applyForm(f: ManagerForm, e: CcfaManager) {
        f.applyTo(e)
        if (f.hasPassword()) e.userPw = encoder.encode(f.password)
    }

    override fun validate(f: ManagerForm, isNew: Boolean, binding: BindingResult) {
        if (isNew && !f.hasPassword()) {
            binding.rejectValue("password", "required", "비밀번호는 필수입니다.")
        }
        // 수정 시 비밀번호를 비워두면 변경하지 않는다는 뜻이므로 정책 검사를 건너뛴다.
        if (f.hasPassword()) {
            PasswordPolicy.validatePassword(f.password, f.passwordConfirm, binding)
        }
    }

    override fun toListView(e: CcfaManager): Any = ManagerRow.of(e)
    override fun toDetailView(e: CcfaManager): Any = ManagerView.of(e)

    override fun populateDetailModel(e: CcfaManager, model: Model) {
        model.addAttribute("companyName", companies.name(e.companyIdx))
        model.addAttribute("lockState", service.lockState(e.userId).orElse(null))
    }

    @PostMapping("/{id}/unlock")
    fun unlock(@PathVariable id: Long, redirect: RedirectAttributes): String {
        service.unlock(id)
        redirect.addFlashAttribute("flashSuccess", "잠금이 해제되었습니다.")
        return "redirect:/managers/$id"
    }
}
