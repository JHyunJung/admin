package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.common.javaIsBlank
import com.crosscert.fidoadmin.common.CrudController
import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.common.JsonPretty
import com.crosscert.fidoadmin.system.entity.CcfaCriteria
import com.crosscert.fidoadmin.system.service.AdminCriteriaService
import org.springframework.stereotype.Controller
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/system/criteria")
class AdminCriteriaController(
    private val service: AdminCriteriaService,
) : CrudController<CcfaCriteria, Long, AdminCriteriaForm, AdminCriteriaSearchForm>() {

    override fun service(): CrudService<CcfaCriteria, Long, AdminCriteriaSearchForm> = service
    override fun basePath(): String = "/system/criteria"
    override fun viewDir(): String = "system/criteria"
    override fun newForm(): AdminCriteriaForm = AdminCriteriaForm()
    override fun toForm(e: CcfaCriteria): AdminCriteriaForm = AdminCriteriaForm.from(e)
    override fun toEntity(f: AdminCriteriaForm): CcfaCriteria = f.toNewEntity()
    override fun applyForm(f: AdminCriteriaForm, e: CcfaCriteria) { f.applyTo(e) }
    override fun toListView(e: CcfaCriteria): Any = AdminCriteriaRow.of(e)
    override fun toDetailView(e: CcfaCriteria): Any = AdminCriteriaView.of(e)

    override fun validate(form: AdminCriteriaForm, isNew: Boolean, binding: BindingResult) {
        val json = form.jsondata
        if (json != null && !json.javaIsBlank() && !JsonPretty.isValidJson(json)) {
            binding.rejectValue("jsondata", "json", "올바른 JSON(객체 또는 배열)이어야 합니다.")
        }
    }
}
