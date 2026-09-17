package com.crosscert.fidoadmin.auth;

import com.crosscert.fidoadmin.common.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/me/password")
@RequiredArgsConstructor
public class PasswordChangeController {

    private final PasswordChangeService service;

    @GetMapping
    public String form(Model model) {
        model.addAttribute("form", new PasswordChangeForm());
        return "auth/password";
    }

    @PostMapping
    public String change(@Valid @ModelAttribute("form") PasswordChangeForm form, BindingResult binding,
                         RedirectAttributes redirect) {
        if (!binding.hasErrors() && !form.getNewPassword().equals(form.getConfirmPassword())) {
            binding.rejectValue("confirmPassword", "mismatch", "새 비밀번호가 일치하지 않습니다.");
        }
        if (binding.hasErrors()) return "auth/password";
        try {
            service.change(TenantContext.require().getUserId(), form.getCurrentPassword(), form.getNewPassword());
        } catch (IllegalArgumentException e) {
            binding.rejectValue("currentPassword", "invalid", e.getMessage());
            return "auth/password";
        }
        redirect.addFlashAttribute("flashSuccess", "비밀번호가 변경되었습니다.");
        return "redirect:/";
    }
}
