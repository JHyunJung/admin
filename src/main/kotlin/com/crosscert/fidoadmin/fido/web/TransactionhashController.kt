package com.crosscert.fidoadmin.fido.web

import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.common.ReadOnlyController
import com.crosscert.fidoadmin.company.service.CompanyLookup
import com.crosscert.fidoadmin.fido.entity.Transactionhash
import com.crosscert.fidoadmin.fido.service.TransactionhashQueryService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.RequestMapping

/** 거래 해시 조회. 목록은 TransactionhashRow(CLOB 제외), 상세는 엔티티 전체. */
@Controller
@RequestMapping("/transaction-hashes")
class TransactionhashController(
    private val service: TransactionhashQueryService,
    private val companies: CompanyLookup,
) : ReadOnlyController<Transactionhash, Long, TransactionhashSearchForm>() {

    override fun service(): CrudService<Transactionhash, Long, TransactionhashSearchForm> = service
    override fun basePath(): String = "/transaction-hashes"
    override fun viewDir(): String = "fido/transactionhash"
    override fun toListView(entity: Transactionhash): Any = TransactionhashRow.of(entity)

    override fun populateDetailModel(entity: Transactionhash, model: Model) {
        model.addAttribute("companyName", companies.name(entity.companyIdx))
    }
}
