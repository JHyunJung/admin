package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.common.CrudController
import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.system.entity.CcfaFields
import com.crosscert.fidoadmin.system.service.FieldService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/system/fields")
class FieldController(
    private val service: FieldService,
) : CrudController<CcfaFields, Long, FieldForm, FieldSearchForm>() {

    override fun service(): CrudService<CcfaFields, Long, FieldSearchForm> = service
    override fun basePath(): String = "/system/fields"
    override fun viewDir(): String = "system/fields"
    override fun newForm(): FieldForm = FieldForm()
    override fun toForm(e: CcfaFields): FieldForm = FieldForm.from(e)
    override fun toEntity(f: FieldForm): CcfaFields = f.toNewEntity()
    override fun applyForm(f: FieldForm, e: CcfaFields) { f.applyTo(e) }

    /**
     * 등록·수정 공통: 코드 그룹으로 지정한 IDX 가 비어 있거나 실제 존재하는 CCFA_OPTION 이어야 한다.
     * 이 검증은 applyForm(엔티티 반영) 이전에 돌기 때문에, 존재하지 않는(허공의) 코드 그룹은
     * DB 에 쓰이기 전에 걸러진다.
     */
    override fun validate(form: FieldForm, isNew: Boolean, binding: BindingResult) {
        if (!service.optionExists(form.optionIdx)) {
            binding.rejectValue("optionIdx", "unknown", "존재하지 않는 코드 그룹입니다.")
        }
    }

    override fun populateFormModel(model: Model) { model.addAttribute("options", service.options()) }
    override fun populateListModel(model: Model) { model.addAttribute("optionNames", optionNames()) }
    override fun populateDetailModel(e: CcfaFields, model: Model) { model.addAttribute("optionNames", optionNames()) }

    private fun optionNames(): Map<Long?, String?> {
        val names = LinkedHashMap<Long?, String?>()
        for (o in service.options()) names[o.idx] = o.optionName
        return names
    }
}
