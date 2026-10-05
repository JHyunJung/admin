package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.fido.entity.Appserver;
import com.crosscert.fidoadmin.fido.service.AppserverService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/appservers")
@RequiredArgsConstructor
public class AppserverController extends CrudController<Appserver, Long, AppserverForm, AppserverSearchForm> {

    private final AppserverService service;
    private final CompanyLookup companies;

    @Override protected CrudService<Appserver, Long, AppserverSearchForm> service() { return service; }
    @Override protected String basePath() { return "/appservers"; }
    @Override protected String viewDir() { return "fido/appserver"; }
    @Override protected AppserverSearchForm newSearchForm() { return new AppserverSearchForm(); }
    @Override protected AppserverForm newForm() { return new AppserverForm(); }
    @Override protected AppserverForm toForm(Appserver e) { return AppserverForm.from(e); }
    @Override protected Appserver toEntity(AppserverForm f) { Appserver a = new Appserver(); f.applyTo(a); return a; }
    @Override protected void applyForm(AppserverForm f, Appserver e) { f.applyTo(e); }

    /**
     * (멤버코드, 멤버키) 중복은 수정 때만 본다. 등록 때는 멤버코드가 아직 없고 생성기가 전체 유일성을 보장한다.
     * 멤버코드는 폼 값이 아니라 저장된 값으로 본다 — 읽기 전용 칸은 변조될 수 있다.
     */
    @Override protected void validate(AppserverForm form, Long id, BindingResult binding) {
        if (id == null) return;
        String storedCode = service.get(id).getMemberCode();
        if (service.existsDuplicate(storedCode, form.getMemberId(), id)) {
            binding.rejectValue("memberId", "duplicate", "이미 등록된 코드와 ID값 입니다.");
        }
    }

    /** 수정 폼이 오류로 다시 그려질 때 변조된 멤버코드가 아니라 저장된 값을 보여 준다. */
    @Override protected void populateFormModel(Model model) {
        Object id = model.getAttribute("id");
        Object form = model.getAttribute("form");
        if (id instanceof Long idx && form instanceof AppserverForm f) {
            f.setMemberCode(service.get(idx).getMemberCode());
        }
    }

    @Override protected void populateDetailModel(Appserver e, Model model) {
        model.addAttribute("companyName", companies.name(e.getCompanyIdx()));
    }
}
