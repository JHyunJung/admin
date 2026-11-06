package com.crosscert.fidoadmin.signup

import com.crosscert.fidoadmin.auth.isJavaBlank
import com.crosscert.fidoadmin.common.PasswordPolicy
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping

/** 가입 신청. 인증 없이 접근한다(SecurityConfig 의 permitAll). */
@Controller
@RequestMapping("/signup")
class SignupController(
    private val service: SignupService,
    private val encoder: PasswordEncoder,
) {

    @GetMapping
    fun form(model: Model): String {
        model.addAttribute("form", SignupForm())
        return "signup/form"
    }

    @PostMapping
    fun apply(@Valid @ModelAttribute("form") form: SignupForm, binding: BindingResult): String {
        PasswordPolicy.validatePassword(form.password, form.passwordConfirm, binding)

        // 중복 아이디를 그 자리에서 알려준다. 이것은 의도된 선택이며 계정 열거를 허용한다.
        // 즉 공격자는 아이디를 하나씩 넣어보며 그 계정이 있는지 확인할 수 있다.
        // 그럼에도 이렇게 두는 이유: 이 도구는 사내망 전용이고 운영자 계정은 한 자릿수라
        // 실제 노출이 작은 반면, 왜 신청이 안 됐는지 모르는 사용자의 비용은 계속 발생한다.
        // 외부에 노출되는 환경으로 바뀌면 이 판단을 다시 해야 한다.
        val userId = form.userId
        if (userId != null && !userId.isJavaBlank() && service.existsUserId(userId)) {
            binding.rejectValue("userId", "duplicate", DUPLICATE_MESSAGE)
        }

        if (binding.hasErrors()) {
            return "signup/form"
        }

        try {
            service.apply(form.userId, encoder.encode(form.password), form.userNm,
                form.userEmail, form.normalizedPhone(), form.normalizedReason())
        } catch (e: DataIntegrityViolationException) {
            // existsUserId 통과 후 저장 직전에 경합으로 중복이 난 경우다.
            // 500 이 아니라 위와 같은 폼 오류로 돌려준다.
            log.info("가입 신청 저장 중 중복 발생: {}", e.message)
            binding.rejectValue("userId", "duplicate", DUPLICATE_MESSAGE)
            return "signup/form"
        }
        return "redirect:/login?signup"
    }

    private companion object {
        private val log = LoggerFactory.getLogger(SignupController::class.java)
        private const val DUPLICATE_MESSAGE = "이미 사용 중인 아이디입니다."
    }
}
