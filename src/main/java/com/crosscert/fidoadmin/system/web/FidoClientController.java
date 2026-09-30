package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.system.entity.CcfaFidoclient;
import com.crosscert.fidoadmin.system.service.FidoClientService;
import com.crosscert.fidoadmin.system.reload.FidoReloadService;
import com.crosscert.fidoadmin.system.reload.ReloadResult;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/system/fido-clients")
@RequiredArgsConstructor
public class FidoClientController extends CrudController<CcfaFidoclient, String, FidoClientForm, FidoClientSearchForm> {

    private final FidoClientService service;
    private final FidoReloadService reload;

    @Override protected CrudService<CcfaFidoclient, String, FidoClientSearchForm> service() { return service; }
    @Override protected String basePath() { return "/system/fido-clients"; }
    @Override protected String viewDir() { return "system/fido-clients"; }
    @Override protected FidoClientSearchForm newSearchForm() { return new FidoClientSearchForm(); }
    @Override protected FidoClientForm newForm() { return new FidoClientForm(); }
    @Override protected FidoClientForm toForm(CcfaFidoclient e) { return FidoClientForm.from(e); }
    @Override protected CcfaFidoclient toEntity(FidoClientForm f) { return f.toNewEntity(); }
    @Override protected void applyForm(FidoClientForm f, CcfaFidoclient e) { f.applyTo(e); }

    /**
     * SERVERCODE 는 CRUD 경로의 {id} 세그먼트로 그대로 쓰인다("/system/fido-clients/{code}").
     * "new" 는 등록 폼 경로("/new")와 겹치고, 점으로만 된 값은 경로 세그먼트로서 특수한 의미를 가져
     * 상세/수정/삭제 링크가 만들어지지 않는다. 문자 집합 자체는 @Pattern 으로 이미 제한했으니
     * 여기서는 예약된 값만 추가로 막는다. "reload" 는 수동 전송 경로(POST /reload)와 겹친다.
     */
    @Override protected void validate(FidoClientForm form, boolean isNew, BindingResult binding) {
        if (isNew && form.getServercode() != null) {
            String code = form.getServercode();
            if (code.equalsIgnoreCase("new") || code.equalsIgnoreCase("reload") || code.matches("\\.+")) {
                binding.rejectValue("servercode", "reserved", "코드로 쓸 수 없는 값입니다: " + code);
            }
        }
    }

    @PostMapping("/reload")
    public String reload(RedirectAttributes redirect) {
        List<ReloadResult> results = reload.reloadNow();
        List<ReloadResult> failed = results.stream().filter(r -> !r.ok()).toList();
        if (results.isEmpty()) {
            redirect.addFlashAttribute("flashError", "reload 를 보낼 FIDO 서버(STATUS=ON)가 없습니다.");
        } else if (failed.isEmpty()) {
            redirect.addFlashAttribute("flashSuccess", "FIDO 서버 " + results.size() + "대에 reload 를 보냈습니다.");
        } else {
            String detail = failed.stream().map(r -> r.servercode() + ": " + r.detail())
                .collect(Collectors.joining(", "));
            redirect.addFlashAttribute("flashError",
                "FIDO 서버 " + results.size() + "대 중 " + failed.size() + "대 reload 실패 — " + detail);
        }
        return "redirect:/system/fido-clients";
    }
}
