package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.ReadOnlyController;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.fido.entity.Challenge;
import com.crosscert.fidoadmin.fido.service.ChallengeQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/** 챌린지 조회. CLOB·민감 컬럼이 없어 엔티티를 그대로 뷰에 넘긴다. */
@Controller
@RequestMapping("/challenges")
@RequiredArgsConstructor
public class ChallengeController extends ReadOnlyController<Challenge, Long, ChallengeSearchForm> {

    private final ChallengeQueryService service;
    private final CompanyLookup companies;

    @Override protected CrudService<Challenge, Long, ChallengeSearchForm> service() { return service; }
    @Override protected String basePath() { return "/challenges"; }
    @Override protected String viewDir() { return "fido/challenge"; }

    @Override protected void populateListModel(Model model) {
        model.addAttribute("companyNames", companies.names());
    }

    @Override protected void populateDetailModel(Challenge entity, Model model) {
        model.addAttribute("companyName", companies.name(entity.getCompanyIdx()));
    }
}
