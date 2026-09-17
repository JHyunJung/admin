package com.crosscert.fidoadmin.fido2.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.fido2.entity.Fido2DemoAccessCode;
import com.crosscert.fidoadmin.fido2.service.Fido2DemoAccessCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/fido2/demo-access-codes")
@RequiredArgsConstructor
public class Fido2DemoAccessCodeController
        extends CrudController<Fido2DemoAccessCode, String, Fido2DemoAccessCodeForm, Fido2DemoAccessCodeSearchForm> {

    private final Fido2DemoAccessCodeService service;

    @Override protected CrudService<Fido2DemoAccessCode, String, Fido2DemoAccessCodeSearchForm> service() { return service; }
    @Override protected String basePath() { return "/fido2/demo-access-codes"; }
    @Override protected String viewDir() { return "fido2/demo-access-code"; }
    @Override protected Fido2DemoAccessCodeSearchForm newSearchForm() { return new Fido2DemoAccessCodeSearchForm(); }
    @Override protected Fido2DemoAccessCodeForm newForm() { return new Fido2DemoAccessCodeForm(); }
    @Override protected Fido2DemoAccessCodeForm toForm(Fido2DemoAccessCode e) { return Fido2DemoAccessCodeForm.from(e); }
    /** 할당형 PK 예외: 등록 시에만 폼의 ACCESSCODE 를 식별자로 쓴다. 서비스가 존재 검사 후 persist 한다. */
    @Override protected Fido2DemoAccessCode toEntity(Fido2DemoAccessCodeForm f) { return f.toNewEntity(); }
    @Override protected void applyForm(Fido2DemoAccessCodeForm f, Fido2DemoAccessCode e) { f.applyTo(e); }
    @Override protected Object toListView(Fido2DemoAccessCode e) { return Fido2DemoAccessCodeView.of(e); }
    @Override protected Object toDetailView(Fido2DemoAccessCode e) { return Fido2DemoAccessCodeView.of(e); }

    @Override protected void validate(Fido2DemoAccessCodeForm form, boolean isNew, BindingResult binding) {
        if (form.getStarttime() != null && form.getEndtime() != null && form.getEndtime().isBefore(form.getStarttime())) {
            binding.rejectValue("endtime", "range", "종료일시는 시작일시보다 빠를 수 없습니다.");
        }
    }
}
