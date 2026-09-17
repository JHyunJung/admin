package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.system.entity.CcfaErrorTable;
import com.crosscert.fidoadmin.system.service.ErrorCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/system/error-codes")
@RequiredArgsConstructor
public class ErrorCodeController extends CrudController<CcfaErrorTable, String, ErrorCodeForm, ErrorCodeSearchForm> {

    private final ErrorCodeService service;

    @Override protected CrudService<CcfaErrorTable, String, ErrorCodeSearchForm> service() { return service; }
    @Override protected String basePath() { return "/system/error-codes"; }
    @Override protected String viewDir() { return "system/error-codes"; }
    @Override protected ErrorCodeSearchForm newSearchForm() { return new ErrorCodeSearchForm(); }
    @Override protected ErrorCodeForm newForm() { return new ErrorCodeForm(); }
    @Override protected ErrorCodeForm toForm(CcfaErrorTable e) { return ErrorCodeForm.from(e); }
    @Override protected CcfaErrorTable toEntity(ErrorCodeForm f) { return f.toNewEntity(); }
    @Override protected void applyForm(ErrorCodeForm f, CcfaErrorTable e) { f.applyTo(e); }

    /**
     * ERROR_CODE 는 CRUD 경로의 {id} 세그먼트로 그대로 쓰인다("/system/error-codes/{code}").
     * "new" 는 등록 폼 경로("/new")와 겹치고, 점으로만 된 값은 경로 세그먼트로서 특수한 의미를 가져
     * 상세/수정/삭제 링크가 만들어지지 않는다. 문자 집합 자체는 @Pattern 으로 이미 제한했으니
     * 여기서는 예약된 값만 추가로 막는다.
     */
    @Override protected void validate(ErrorCodeForm form, boolean isNew, BindingResult binding) {
        if (isNew && form.getErrorCode() != null) {
            String code = form.getErrorCode();
            if (code.equalsIgnoreCase("new") || code.matches("\\.+")) {
                binding.rejectValue("errorCode", "reserved", "코드로 쓸 수 없는 값입니다: " + code);
            }
        }
    }
}
