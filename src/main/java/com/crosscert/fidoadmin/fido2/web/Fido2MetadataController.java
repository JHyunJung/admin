package com.crosscert.fidoadmin.fido2.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.fido2.entity.Fido2Metadata;
import com.crosscert.fidoadmin.fido2.service.Fido2MetadataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/fido2/metadata")
@RequiredArgsConstructor
public class Fido2MetadataController extends CrudController<Fido2Metadata, Long, Fido2MetadataForm, Fido2MetadataSearchForm> {

    private final Fido2MetadataService service;

    @Override protected CrudService<Fido2Metadata, Long, Fido2MetadataSearchForm> service() { return service; }
    @Override protected String basePath() { return "/fido2/metadata"; }
    @Override protected String viewDir() { return "fido2/metadata"; }
    @Override protected Fido2MetadataSearchForm newSearchForm() { return new Fido2MetadataSearchForm(); }
    @Override protected Fido2MetadataForm newForm() { return new Fido2MetadataForm(); }
    @Override protected Fido2MetadataForm toForm(Fido2Metadata e) { return Fido2MetadataForm.from(e); }
    @Override protected Fido2Metadata toEntity(Fido2MetadataForm f) { Fido2Metadata m = new Fido2Metadata(); f.applyTo(m); return m; }
    @Override protected void applyForm(Fido2MetadataForm f, Fido2Metadata e) { f.applyTo(e); }
    /** 목록은 CLOB 제외. 상세는 전체 컬럼(민감 컬럼 없음)이라 엔티티 그대로. */
    @Override protected Object toListView(Fido2Metadata e) { return Fido2MetadataRow.of(e); }
}
