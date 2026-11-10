package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.common.CrudController
import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.system.entity.CcfaSystemInfo
import com.crosscert.fidoadmin.system.service.SystemInfoService
import org.springframework.stereotype.Controller
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/system/info")
class SystemInfoController(
    private val service: SystemInfoService,
) : CrudController<CcfaSystemInfo, String, SystemInfoForm, SystemInfoSearchForm>() {

    override fun service(): CrudService<CcfaSystemInfo, String, SystemInfoSearchForm> = service
    override fun basePath(): String = "/system/info"
    override fun viewDir(): String = "system/info"
    override fun newForm(): SystemInfoForm = SystemInfoForm()
    override fun toForm(e: CcfaSystemInfo): SystemInfoForm = SystemInfoForm.from(e)
    override fun toEntity(f: SystemInfoForm): CcfaSystemInfo = f.toNewEntity()
    override fun applyForm(f: SystemInfoForm, e: CcfaSystemInfo) { f.applyTo(e) }

    /**
     * PROP_KEY 는 CRUD 경로의 {id} 세그먼트로 그대로 쓰인다("/system/info/{key}").
     * "new" 는 등록 폼 경로("/new")와 겹치고, 점으로만 된 값은 경로 세그먼트로서 특수한 의미를 가져
     * 상세/수정/삭제 링크가 만들어지지 않는다. 문자 집합 자체는 @Pattern 으로 이미 제한했으니
     * 여기서는 예약된 값만 추가로 막는다.
     */
    override fun validate(form: SystemInfoForm, isNew: Boolean, binding: BindingResult) {
        rejectReservedPathId(form.propKey, isNew, binding, "propKey", "키")
    }
}
