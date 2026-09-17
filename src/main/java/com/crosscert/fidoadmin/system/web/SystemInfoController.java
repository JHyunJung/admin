package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.system.entity.CcfaSystemInfo;
import com.crosscert.fidoadmin.system.service.SystemInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/system/info")
@RequiredArgsConstructor
public class SystemInfoController extends CrudController<CcfaSystemInfo, String, SystemInfoForm, SystemInfoSearchForm> {

    private final SystemInfoService service;

    @Override protected CrudService<CcfaSystemInfo, String, SystemInfoSearchForm> service() { return service; }
    @Override protected String basePath() { return "/system/info"; }
    @Override protected String viewDir() { return "system/info"; }
    @Override protected SystemInfoSearchForm newSearchForm() { return new SystemInfoSearchForm(); }
    @Override protected SystemInfoForm newForm() { return new SystemInfoForm(); }
    @Override protected SystemInfoForm toForm(CcfaSystemInfo e) { return SystemInfoForm.from(e); }
    @Override protected CcfaSystemInfo toEntity(SystemInfoForm f) { return f.toNewEntity(); }
    @Override protected void applyForm(SystemInfoForm f, CcfaSystemInfo e) { f.applyTo(e); }

    /**
     * PROP_KEY 는 CRUD 경로의 {id} 세그먼트로 그대로 쓰인다("/system/info/{key}").
     * "new" 는 등록 폼 경로("/new")와 겹치고, 점으로만 된 값은 경로 세그먼트로서 특수한 의미를 가져
     * 상세/수정/삭제 링크가 만들어지지 않는다. 문자 집합 자체는 @Pattern 으로 이미 제한했으니
     * 여기서는 예약된 값만 추가로 막는다.
     */
    @Override protected void validate(SystemInfoForm form, boolean isNew, BindingResult binding) {
        if (isNew && form.getPropKey() != null) {
            String key = form.getPropKey();
            if (key.equalsIgnoreCase("new") || key.matches("\\.+")) {
                binding.rejectValue("propKey", "reserved", "키로 쓸 수 없는 값입니다: " + key);
            }
        }
    }
}
