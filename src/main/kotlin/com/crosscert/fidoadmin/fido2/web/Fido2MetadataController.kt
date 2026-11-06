package com.crosscert.fidoadmin.fido2.web

import com.crosscert.fidoadmin.common.CrudController
import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.fido2.entity.Fido2Metadata
import com.crosscert.fidoadmin.fido2.service.Fido2MetadataService
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/fido2/metadata")
class Fido2MetadataController(
    private val service: Fido2MetadataService,
) : CrudController<Fido2Metadata, Long, Fido2MetadataForm, Fido2MetadataSearchForm>() {

    override fun service(): CrudService<Fido2Metadata, Long, Fido2MetadataSearchForm> = service
    override fun basePath(): String = "/fido2/metadata"
    override fun viewDir(): String = "fido2/metadata"
    override fun newForm(): Fido2MetadataForm = Fido2MetadataForm()
    override fun toForm(e: Fido2Metadata): Fido2MetadataForm = Fido2MetadataForm.from(e)
    override fun toEntity(f: Fido2MetadataForm): Fido2Metadata { val m = Fido2Metadata(); f.applyTo(m); return m }
    override fun applyForm(f: Fido2MetadataForm, e: Fido2Metadata) { f.applyTo(e) }
    /** 목록은 CLOB 제외. 상세는 전체 컬럼(민감 컬럼 없음)이라 엔티티 그대로. */
    override fun toListView(e: Fido2Metadata): Any = Fido2MetadataRow.of(e)
}
