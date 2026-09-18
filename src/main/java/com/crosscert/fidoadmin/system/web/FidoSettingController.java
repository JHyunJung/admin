package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.system.service.FidoSettingService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * FIDO 서버 설정 화면. 세 섹션(인증서·부가기능·알림메일)을 한 페이지에서 한 번에 저장한다.
 *
 * <p>SUPER 전용이다. URL 은 {@code SecurityConfig} 의 {@code /system/**} 규칙이 막고,
 * 값은 유효 테넌트로만 저장된다({@link FidoSettingService}).
 *
 * <p>체크박스는 선택된 것만 파라미터로 오므로 {@code @RequestParam Map} 에 키가 아예
 * 없을 수 있다. 그래서 폼에 같은 이름의 hidden 빈 값을 함께 두어 "아무것도 선택 안 함"과
 * "필드가 전송되지 않음"을 구분한다(템플릿 참고).
 */
@Controller
@RequestMapping("/system/settings")
@RequiredArgsConstructor
public class FidoSettingController {

    private final FidoSettingService service;

    @GetMapping
    public String form(Model model) {
        model.addAttribute("values", service.load());
        model.addAttribute("sections", FidoSettingKey.bySection());
        model.addAttribute("certVerifyOptions", FidoSettingKey.CERT_VERIFY_OPTIONS);
        model.addAttribute("tcRetentionOptions", FidoSettingKey.TC_RETENTION_OPTIONS);
        model.addAttribute("authResponseChoices", FidoSettingKey.AUTH_RESPONSE_CHOICES);
        return "system/settings/form";
    }

    @PostMapping
    public String save(@RequestParam Map<String, String> params, RedirectAttributes redirect) {
        service.save(params);
        redirect.addFlashAttribute("flashSuccess", "설정을 저장했습니다.");
        return "redirect:/system/settings";
    }
}
