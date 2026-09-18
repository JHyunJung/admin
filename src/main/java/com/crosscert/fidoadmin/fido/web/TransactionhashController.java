package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.common.ReadOnlyController;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.fido.entity.Transactionhash;
import com.crosscert.fidoadmin.fido.service.TransactionhashQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/** 거래 해시 조회. 목록은 TransactionhashRow(CLOB 제외), 상세는 엔티티 전체. */
@Controller
@RequestMapping("/transaction-hashes")
@RequiredArgsConstructor
public class TransactionhashController extends ReadOnlyController<Transactionhash, Long, TransactionhashSearchForm> {

    private final TransactionhashQueryService service;
    private final CompanyLookup companies;

    @Override protected CrudService<Transactionhash, Long, TransactionhashSearchForm> service() { return service; }
    @Override protected String basePath() { return "/transaction-hashes"; }
    @Override protected String viewDir() { return "fido/transactionhash"; }
    @Override protected Object toListView(Transactionhash entity) { return TransactionhashRow.of(entity); }

    @Override protected void populateDetailModel(Transactionhash entity, Model model) {
        model.addAttribute("companyName", companies.name(entity.getCompanyIdx()));
    }
}
