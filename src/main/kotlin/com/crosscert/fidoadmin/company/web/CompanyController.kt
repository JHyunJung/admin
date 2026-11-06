package com.crosscert.fidoadmin.company.web

import com.crosscert.fidoadmin.common.CrudController
import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.company.entity.CcfaCompany
import com.crosscert.fidoadmin.company.service.CompanyService
import org.springframework.stereotype.Controller
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/companies")
class CompanyController(
    private val service: CompanyService,
) : CrudController<CcfaCompany, Long, CompanyForm, CompanySearchForm>() {

    override fun service(): CrudService<CcfaCompany, Long, CompanySearchForm> = service
    override fun basePath(): String = "/companies"
    override fun viewDir(): String = "company/company"
    override fun newForm(): CompanyForm = CompanyForm()
    override fun toForm(e: CcfaCompany): CompanyForm = CompanyForm.from(e)
    override fun toEntity(f: CompanyForm): CcfaCompany { val c = CcfaCompany(); f.applyTo(c); return c }
    override fun applyForm(f: CompanyForm, e: CcfaCompany) { f.applyTo(e) }

    override fun createdMessage(saved: CcfaCompany): String =
        "등록되었습니다. 모든 AAID 가 비활성 상태로 시작합니다. AAID(정책) 화면에서 허용할 인증기를 켜세요."

    /**
     * VENDOR_CODE 중복 검사. DB 에 유니크 제약이 없어 여기서 막는다.
     * 수정 때는 자기 자신을 제외해야 하므로 id 를 받는 오버로드를 쓴다.
     */
    override fun validate(form: CompanyForm, id: Long?, binding: BindingResult) {
        service.findByVendorCode(form.vendorCode)
            .filter { other -> id == null || id != other.idx }
            .ifPresent { other ->
                binding.rejectValue("vendorCode", "duplicate",
                    "이미 사용 중인 업체 코드입니다 (고객사 #" + other.idx + " " + other.companyName + ")")
            }
    }
}
