package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.common.CrudController
import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.company.service.CompanyLookup
import com.crosscert.fidoadmin.system.entity.CcfaSystemProp
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId
import com.crosscert.fidoadmin.system.service.SystemPropService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/system/props")
class SystemPropController(
    private val service: SystemPropService,
    private val companies: CompanyLookup,
    private val tenant: TenantContext,
) : CrudController<CcfaSystemProp, CcfaSystemPropId, SystemPropForm, SystemPropSearchForm>() {

    override fun service(): CrudService<CcfaSystemProp, CcfaSystemPropId, SystemPropSearchForm> = service
    override fun basePath(): String = "/system/props"
    override fun viewDir(): String = "system/props"
    override fun newForm(): SystemPropForm = SystemPropForm()
    override fun toForm(e: CcfaSystemProp): SystemPropForm = SystemPropForm.from(e)

    /**
     * 복합키(PROP_KEY + COMPANY_IDX)의 COMPANY_IDX 를 유효 테넌트로 채운다. 고객사 select 가 사라졌으므로 폼에는 값이 없다.
     *
     * Java 에서는 protected 였고 같은 패키지의 테스트(SystemPropTenantTest)가 직접 불렀다. Kotlin 의 protected 는
     * 패키지 접근을 주지 않으므로 public 으로 연다(핸들러 매핑 애너테이션이 없어 URL 로는 노출되지 않는다).
     */
    public override fun toEntity(f: SystemPropForm): CcfaSystemProp = f.toNewEntity(tenant.companyIdx())
    override fun applyForm(f: SystemPropForm, e: CcfaSystemProp) { f.applyTo(e) }
    override fun toListView(e: CcfaSystemProp): Any = SystemPropRow.of(e)
    override fun toDetailView(e: CcfaSystemProp): Any = SystemPropRow.of(e)

    /**
     * PROP_KEY 는 CRUD 경로의 {id} 세그먼트 조합으로 그대로 쓰인다("/system/props/{key}@{companyIdx}" 등).
     * "new" 는 등록 폼 경로("/new")와 겹치고, 점으로만 된 값("." , "..")은 경로 세그먼트로서
     * 특수한 의미를 가져 상세/수정/삭제 링크가 만들어지지 않는다. 문자 집합 자체는
     * @Pattern 으로 이미 제한했으니 여기서는 예약된 값만 추가로 막는다.
     */
    override fun validate(form: SystemPropForm, isNew: Boolean, binding: BindingResult) {
        val key = form.propKey
        if (isNew && key != null) {
            if (key.equals("new", ignoreCase = true) || key.matches(DOTS_ONLY)) {
                binding.rejectValue("propKey", "reserved", "키로 쓸 수 없는 값입니다: $key")
            }
        }
    }

    override fun populateDetailModel(e: CcfaSystemProp, model: Model) {
        model.addAttribute("companyName", companies.name(e.id!!.companyIdx))
    }

    private companion object {
        val DOTS_ONLY = Regex("\\.+")
    }
}
