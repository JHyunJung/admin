package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.ReadOnlyController;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.fido.entity.TransactionConfirmation;
import com.crosscert.fidoadmin.fido.service.TransactionConfirmationQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/** 거래 확인(TC) 조회. 목록은 TransactionConfirmationRow(CLOB 제외), 상세는 엔티티 전체. */
@Controller
@RequestMapping("/transaction-confirmations")
@RequiredArgsConstructor
public class TransactionConfirmationController
        extends ReadOnlyController<TransactionConfirmation, Long, TransactionConfirmationSearchForm> {

    private final TransactionConfirmationQueryService service;
    private final CompanyLookup companies;

    @Override protected CrudService<TransactionConfirmation, Long, TransactionConfirmationSearchForm> service() { return service; }
    @Override protected String basePath() { return "/transaction-confirmations"; }
    @Override protected String viewDir() { return "fido/transaction-confirmation"; }
    @Override protected Object toListView(TransactionConfirmation entity) { return TransactionConfirmationRow.of(entity); }

    @Override protected void populateListModel(Model model) {
        model.addAttribute("companyNames", companies.names());
    }

    @Override protected void populateDetailModel(TransactionConfirmation entity, Model model) {
        model.addAttribute("companyName", companies.name(entity.getCompanyIdx()));
    }
}
