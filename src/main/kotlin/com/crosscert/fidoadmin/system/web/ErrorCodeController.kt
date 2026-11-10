package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.common.CrudController
import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.system.entity.CcfaErrorTable
import com.crosscert.fidoadmin.system.service.ErrorCodeService
import org.springframework.stereotype.Controller
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/system/error-codes")
class ErrorCodeController(
    private val service: ErrorCodeService,
) : CrudController<CcfaErrorTable, String, ErrorCodeForm, ErrorCodeSearchForm>() {

    override fun service(): CrudService<CcfaErrorTable, String, ErrorCodeSearchForm> = service
    override fun basePath(): String = "/system/error-codes"
    override fun viewDir(): String = "system/error-codes"
    override fun newForm(): ErrorCodeForm = ErrorCodeForm()
    override fun toForm(e: CcfaErrorTable): ErrorCodeForm = ErrorCodeForm.from(e)
    override fun toEntity(f: ErrorCodeForm): CcfaErrorTable = f.toNewEntity()
    override fun applyForm(f: ErrorCodeForm, e: CcfaErrorTable) { f.applyTo(e) }

    /**
     * ERROR_CODE 는 CRUD 경로의 {id} 세그먼트로 그대로 쓰인다("/system/error-codes/{code}").
     * "new" 는 등록 폼 경로("/new")와 겹치고, 점으로만 된 값은 경로 세그먼트로서 특수한 의미를 가져
     * 상세/수정/삭제 링크가 만들어지지 않는다. 문자 집합 자체는 @Pattern 으로 이미 제한했으니
     * 여기서는 예약된 값만 추가로 막는다.
     */
    override fun validate(form: ErrorCodeForm, isNew: Boolean, binding: BindingResult) {
        rejectReservedPathId(form.errorCode, isNew, binding, "errorCode", "코드")
    }
}
