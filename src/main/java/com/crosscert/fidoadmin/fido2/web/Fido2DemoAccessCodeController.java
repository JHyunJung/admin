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
        /*
         * ACCESSCODE 는 CRUD 경로의 {id} 세그먼트 그대로 쓰인다("/fido2/demo-access-codes/{id}" 등).
         * "new" 는 등록 폼 경로("/new")와 겹치고, 점으로만 된 값("." , "..")은 경로 세그먼트로서
         * 특수한 의미를 가져 상세/수정/삭제 링크가 만들어지지 않는다. 문자 집합 자체는
         * @Pattern 으로 이미 제한했으니 여기서는 예약된 값만 추가로 막는다.
         */
        if (isNew && form.getAccesscode() != null) {
            String code = form.getAccesscode();
            if (code.equalsIgnoreCase("new") || code.matches("\\.+")) {
                binding.rejectValue("accesscode", "reserved", "접근코드로 쓸 수 없는 값입니다: " + code);
            }
        }
    }
}
