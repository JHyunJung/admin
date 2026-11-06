package com.crosscert.fidoadmin.fido2.web

import com.crosscert.fidoadmin.common.CrudController
import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.fido2.entity.Fido2DemoAccessCode
import com.crosscert.fidoadmin.fido2.service.Fido2DemoAccessCodeService
import org.springframework.stereotype.Controller
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/fido2/demo-access-codes")
class Fido2DemoAccessCodeController(
    private val service: Fido2DemoAccessCodeService,
) : CrudController<Fido2DemoAccessCode, String, Fido2DemoAccessCodeForm, Fido2DemoAccessCodeSearchForm>() {

    override fun service(): CrudService<Fido2DemoAccessCode, String, Fido2DemoAccessCodeSearchForm> = service
    override fun basePath(): String = "/fido2/demo-access-codes"
    override fun viewDir(): String = "fido2/demo-access-code"
    override fun newForm(): Fido2DemoAccessCodeForm = Fido2DemoAccessCodeForm()
    override fun toForm(e: Fido2DemoAccessCode): Fido2DemoAccessCodeForm = Fido2DemoAccessCodeForm.from(e)
    /** 할당형 PK 예외: 등록 시에만 폼의 ACCESSCODE 를 식별자로 쓴다. 서비스가 존재 검사 후 persist 한다. */
    override fun toEntity(f: Fido2DemoAccessCodeForm): Fido2DemoAccessCode = f.toNewEntity()
    override fun applyForm(f: Fido2DemoAccessCodeForm, e: Fido2DemoAccessCode) { f.applyTo(e) }
    override fun toListView(e: Fido2DemoAccessCode): Any = Fido2DemoAccessCodeView.of(e)
    override fun toDetailView(e: Fido2DemoAccessCode): Any = Fido2DemoAccessCodeView.of(e)

    override fun validate(form: Fido2DemoAccessCodeForm, isNew: Boolean, binding: BindingResult) {
        val start = form.starttime
        val end = form.endtime
        if (start != null && end != null && end.isBefore(start)) {
            binding.rejectValue("endtime", "range", "종료일시는 시작일시보다 빠를 수 없습니다.")
        }
        /*
         * ACCESSCODE 는 CRUD 경로의 {id} 세그먼트 그대로 쓰인다("/fido2/demo-access-codes/{id}" 등).
         * "new" 는 등록 폼 경로("/new")와 겹치고, 점으로만 된 값("." , "..")은 경로 세그먼트로서
         * 특수한 의미를 가져 상세/수정/삭제 링크가 만들어지지 않는다. 문자 집합 자체는
         * @Pattern 으로 이미 제한했으니 여기서는 예약된 값만 추가로 막는다.
         */
        val code = form.accesscode
        if (isNew && code != null) {
            if (code.equals("new", ignoreCase = true) || code.matches(Regex("\\.+"))) {
                binding.rejectValue("accesscode", "reserved", "접근코드로 쓸 수 없는 값입니다: $code")
            }
        }
    }
}
