package com.crosscert.fidoadmin.manager.web

import com.crosscert.fidoadmin.common.CrudController
import com.crosscert.fidoadmin.common.PasswordPolicy
import com.crosscert.fidoadmin.manager.entity.CcfaManager
import com.crosscert.fidoadmin.manager.service.ManagerAccountService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/**
 * 운영자 계정 화면 두 개(/managers, /managers/super)의 공통 훅과 잠금 해제.
 * 폼 변환·비밀번호 인코딩·비밀번호 규칙이 한쪽에만 바뀌지 않도록 여기 한 벌만 둔다
 * (서비스 계층의 [ManagerAccountService] 와 같은 이유).
 * 하위 클래스에는 서비스·basePath·viewDir·상세 모델만 남긴다.
 *
 * 어노테이션이 없는 추상 클래스라 plugin.spring 이 멤버를 열지 않으므로 [unlock] 은 `open` 으로 둔다.
 */
abstract class ManagerAccountController(
    private val accounts: ManagerAccountService,
    private val encoder: PasswordEncoder,
) : CrudController<CcfaManager, Long, ManagerForm, ManagerSearchForm>() {

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

    @PostMapping("/{id}/unlock")
    open fun unlock(@PathVariable id: Long, redirect: RedirectAttributes): String {
        accounts.unlock(id)
        redirect.addFlashAttribute("flashSuccess", "잠금이 해제되었습니다.")
        return "redirect:" + basePath() + "/" + id
    }
}
