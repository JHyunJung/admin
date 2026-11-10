package com.crosscert.fidoadmin.fido.web

import com.crosscert.fidoadmin.common.CrudController
import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.company.service.CompanyLookup
import com.crosscert.fidoadmin.fido.entity.Appserver
import com.crosscert.fidoadmin.fido.service.AppserverService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/appservers")
class AppserverController(
    private val service: AppserverService,
    private val companies: CompanyLookup,
) : CrudController<Appserver, Long, AppserverForm, AppserverSearchForm>() {

    override fun service(): CrudService<Appserver, Long, AppserverSearchForm> = service
    override fun basePath(): String = "/appservers"
    override fun viewDir(): String = "fido/appserver"
    override fun newForm(): AppserverForm = AppserverForm()
    override fun toForm(e: Appserver): AppserverForm = AppserverForm.from(e)
    override fun toEntity(f: AppserverForm): Appserver { val a = Appserver(); f.applyTo(a); return a }
    override fun applyForm(f: AppserverForm, e: Appserver) { f.applyTo(e) }

    /**
     * (멤버코드, 멤버키) 중복은 수정 때만 본다. 등록 때는 멤버코드가 아직 없고 생성기가 전체 유일성을 보장한다.
     * 멤버코드는 폼 값이 아니라 저장된 값으로 본다 — 읽기 전용 칸은 변조될 수 있다.
     *
     * 읽은 저장 값을 폼에도 넣어 둔다. 수정 폼이 오류로 다시 그려질 때 변조된 멤버코드가 아니라
     * 저장된 값을 보여 주기 위해서다(update 는 다시 그리기 전에 항상 이 검증을 거친다).
     * applyTo 는 memberCode 를 엔티티에 옮기지 않으므로 저장 결과에는 영향이 없다.
     */
    override fun validate(form: AppserverForm, id: Long?, binding: BindingResult) {
        if (id == null) return
        val storedCode = service.get(id).memberCode
        form.memberCode = storedCode
        if (service.existsDuplicate(storedCode, form.memberId, id)) {
            binding.rejectValue("memberId", "duplicate", "이미 등록된 코드와 ID값 입니다.")
        }
    }

    override fun populateDetailModel(e: Appserver, model: Model) {
        model.addAttribute("companyName", companies.name(e.companyIdx))
    }
}
