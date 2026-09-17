package com.crosscert.fidoadmin.signup;

import com.crosscert.fidoadmin.common.PasswordPolicy;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** 가입 신청. 인증 없이 접근한다(SecurityConfig 의 permitAll). */
@Slf4j
@Controller
@RequestMapping("/signup")
@RequiredArgsConstructor
public class SignupController {

    private final SignupService service;
    private final PasswordEncoder encoder;

    private static final String DUPLICATE_MESSAGE = "이미 사용 중인 아이디입니다.";

    @GetMapping
    public String form(Model model) {
        model.addAttribute("form", new SignupForm());
        return "signup/form";
    }

    @PostMapping
    public String apply(@Valid @ModelAttribute("form") SignupForm form, BindingResult binding) {
        PasswordPolicy.validatePassword(form.getPassword(), form.getPasswordConfirm(), binding);

        // 중복 아이디를 그 자리에서 알려준다. 이것은 의도된 선택이며 계정 열거를 허용한다.
        // 즉 공격자는 아이디를 하나씩 넣어보며 그 계정이 있는지 확인할 수 있다.
        // 그럼에도 이렇게 두는 이유: 이 도구는 사내망 전용이고 운영자 계정은 한 자릿수라
        // 실제 노출이 작은 반면, 왜 신청이 안 됐는지 모르는 사용자의 비용은 계속 발생한다.
        // 외부에 노출되는 환경으로 바뀌면 이 판단을 다시 해야 한다(설계서 6.1).
        if (form.getUserId() != null && !form.getUserId().isBlank()
            && service.existsUserId(form.getUserId())) {
            binding.rejectValue("userId", "duplicate", DUPLICATE_MESSAGE);
        }

        if (binding.hasErrors()) {
            return "signup/form";
        }

        try {
            service.apply(form.getUserId(), encoder.encode(form.getPassword()), form.getUserNm(),
                form.getUserEmail(), form.normalizedPhone(), form.normalizedReason());
        } catch (DataIntegrityViolationException e) {
            // existsUserId 통과 후 저장 직전에 경합으로 중복이 난 경우다.
            // 500 이 아니라 위와 같은 폼 오류로 돌려준다.
            log.info("가입 신청 저장 중 중복 발생: {}", e.getMessage());
            binding.rejectValue("userId", "duplicate", DUPLICATE_MESSAGE);
            return "signup/form";
        }
        return "redirect:/login?signup";
    }
}
