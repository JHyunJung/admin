package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.ReadOnlyController;
import com.crosscert.fidoadmin.fido.entity.Criteria;
import com.crosscert.fidoadmin.fido.service.CriteriaQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/** 인증기기 기준(UAF CRITERIA) 조회. SUPER 전용(SecurityConfig /criteria/**, 서비스 이중 차단). */
@Controller
@RequestMapping("/criteria")
@RequiredArgsConstructor
public class CriteriaController extends ReadOnlyController<Criteria, Long, CriteriaSearchForm> {

    private final CriteriaQueryService service;

    @Override protected CrudService<Criteria, Long, CriteriaSearchForm> service() { return service; }
    @Override protected String basePath() { return "/criteria"; }
    @Override protected String viewDir() { return "fido/criteria"; }
    @Override protected Object toListView(Criteria entity) { return CriteriaRow.of(entity); }
    @Override protected Object toDetailView(Criteria entity) { return CriteriaView.of(entity); }
}
