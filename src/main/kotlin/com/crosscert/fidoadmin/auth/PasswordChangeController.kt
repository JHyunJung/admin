package com.crosscert.fidoadmin.auth

import com.crosscert.fidoadmin.common.TenantContext
import jakarta.servlet.http.HttpSession
import jakarta.validation.Valid
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
@RequestMapping("/me/password")
class PasswordChangeController(
    private val service: PasswordChangeService,
    private val tenant: TenantContext,
) {

    @GetMapping
    fun form(model: Model, session: HttpSession): String {
        model.addAttribute("expiredDays", session.getAttribute(PasswordExpiredInterceptor.SESSION_ATTR))
        model.addAttribute("form", PasswordChangeForm())
        return "auth/password"
    }

    @PostMapping
    fun change(@Valid @ModelAttribute("form") form: PasswordChangeForm, binding: BindingResult,
               redirect: RedirectAttributes, model: Model, session: HttpSession): String {
        model.addAttribute("expiredDays", session.getAttribute(PasswordExpiredInterceptor.SESSION_ATTR))
        // Java 의 form.getNewPassword().equals(...) 와 같이, 검증을 통과했는데 null 이면 NPE 다.
        if (!binding.hasErrors() && form.newPassword!! != form.confirmPassword) {
            binding.rejectValue("confirmPassword", "mismatch", "새 비밀번호가 일치하지 않습니다.")
        }
        if (binding.hasErrors()) return "auth/password"
        try {
            service.change(tenant.require().userId, form.currentPassword, form.newPassword)
        } catch (e: SamePasswordException) {
            binding.rejectValue("newPassword", "same", e.message.orEmpty())
            return "auth/password"
        } catch (e: IllegalArgumentException) {
            binding.rejectValue("currentPassword", "invalid", e.message.orEmpty())
            return "auth/password"
        }
        session.removeAttribute(PasswordExpiredInterceptor.SESSION_ATTR)
        redirect.addFlashAttribute("flashSuccess", "비밀번호가 변경되었습니다.")
        return "redirect:/"
    }
}
