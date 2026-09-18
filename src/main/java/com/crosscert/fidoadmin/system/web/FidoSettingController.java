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
 * <p>토글(체크박스)은 켜졌을 때만 파라미터로 온다. 그래서 폼이 같은 이름의 hidden 을
 * 앞에 두어 "끔" 값을 항상 보낸다 — 그렇지 않으면 토글을 끄는 조작이 전달되지 않는다.
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
        return "system/settings/form";
    }

    @PostMapping
    public String save(@RequestParam Map<String, String> params, RedirectAttributes redirect) {
        service.save(params);
        redirect.addFlashAttribute("flashSuccess", "설정을 저장했습니다.");
        return "redirect:/system/settings";
    }
}
