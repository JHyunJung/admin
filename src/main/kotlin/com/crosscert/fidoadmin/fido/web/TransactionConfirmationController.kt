package com.crosscert.fidoadmin.fido.web

import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.common.ReadOnlyController
import com.crosscert.fidoadmin.company.service.CompanyLookup
import com.crosscert.fidoadmin.fido.entity.TransactionConfirmation
import com.crosscert.fidoadmin.fido.service.TransactionConfirmationQueryService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.RequestMapping

/** 거래 확인(TC) 조회. 목록은 TransactionConfirmationRow(CLOB 제외), 상세는 엔티티 전체. */
@Controller
@RequestMapping("/transaction-confirmations")
class TransactionConfirmationController(
    private val service: TransactionConfirmationQueryService,
    private val companies: CompanyLookup,
) : ReadOnlyController<TransactionConfirmation, Long, TransactionConfirmationSearchForm>() {

    override fun service(): CrudService<TransactionConfirmation, Long, TransactionConfirmationSearchForm> = service
    override fun basePath(): String = "/transaction-confirmations"
    override fun viewDir(): String = "fido/transaction-confirmation"
    override fun toListView(entity: TransactionConfirmation): Any = TransactionConfirmationRow.of(entity)

    override fun populateDetailModel(entity: TransactionConfirmation, model: Model) {
        model.addAttribute("companyName", companies.name(entity.companyIdx))
    }
}
