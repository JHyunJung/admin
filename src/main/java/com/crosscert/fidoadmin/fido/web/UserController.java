package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.ReadOnlyController;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.fido.entity.Userinfo;
import com.crosscert.fidoadmin.fido.service.UserinfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 사용자 조회 + 상태 변경('O'/'X'). 등록·수정·삭제 없음. */
@Controller
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController extends ReadOnlyController<Userinfo, Long, UserSearchForm> {

    private final UserinfoService service;
    private final CompanyLookup companies;

    @Override protected CrudService<Userinfo, Long, UserSearchForm> service() { return service; }
    @Override protected String basePath() { return "/users"; }
    @Override protected String viewDir() { return "fido/user"; }
    @Override protected Object toListView(Userinfo e) { return UserRow.from(e); }
    @Override protected Object toDetailView(Userinfo e) { return UserView.from(e); }

    @Override protected void populateListModel(Model model) {
        model.addAttribute("companyNames", companies.names());
    }
    @Override protected void populateDetailModel(Userinfo e, Model model) {
        model.addAttribute("companyName", companies.name(e.getCompanyIdx()));
        model.addAttribute("statuses", UserinfoService.STATUSES.stream().sorted().toList());
    }

    @PostMapping("/{id}/status")
    public String changeStatus(@PathVariable Long id, @RequestParam String status, RedirectAttributes redirect) {
        try {
            service.changeStatus(id, status);
            redirect.addFlashAttribute("flashSuccess", "상태가 변경되었습니다.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/users/" + id;
    }
}
