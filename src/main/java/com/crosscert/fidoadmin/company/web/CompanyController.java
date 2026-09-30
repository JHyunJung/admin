package com.crosscert.fidoadmin.company.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.company.service.CompanyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/companies")
@RequiredArgsConstructor
public class CompanyController extends CrudController<CcfaCompany, Long, CompanyForm, CompanySearchForm> {

    private final CompanyService service;

    @Override protected CrudService<CcfaCompany, Long, CompanySearchForm> service() { return service; }
    @Override protected String basePath() { return "/companies"; }
    @Override protected String viewDir() { return "company/company"; }
    @Override protected CompanySearchForm newSearchForm() { return new CompanySearchForm(); }
    @Override protected CompanyForm newForm() { return new CompanyForm(); }
    @Override protected CompanyForm toForm(CcfaCompany e) { return CompanyForm.from(e); }
    @Override protected CcfaCompany toEntity(CompanyForm f) { CcfaCompany c = new CcfaCompany(); f.applyTo(c); return c; }
    @Override protected void applyForm(CompanyForm f, CcfaCompany e) { f.applyTo(e); }

    @Override protected String createdMessage(CcfaCompany saved) {
        return "등록되었습니다. 모든 AAID 가 비활성 상태로 시작합니다. AAID(정책) 화면에서 허용할 인증기를 켜세요.";
    }

    /**
     * VENDOR_CODE 중복 검사. DB 에 유니크 제약이 없어 여기서 막는다.
     * 수정 때는 자기 자신을 제외해야 하므로 id 를 받는 오버로드를 쓴다.
     */
    @Override
    protected void validate(CompanyForm form, Long id, BindingResult binding) {
        service.findByVendorCode(form.getVendorCode())
            .filter(other -> id == null || !id.equals(other.getIdx()))
            .ifPresent(other -> binding.rejectValue("vendorCode", "duplicate",
                "이미 사용 중인 업체 코드입니다 (고객사 #" + other.getIdx() + " " + other.getCompanyName() + ")"));
    }
}
