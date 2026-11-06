package com.crosscert.fidoadmin.fido.web

import com.crosscert.fidoadmin.common.SearchForm

/** TRANSACTION_CONFIRMATION 검색 조건. 기간은 기반의 fromDate/toDate(CREATEDTIME 기준). */
class TransactionConfirmationSearchForm : SearchForm() {
    var userid: String? = null
    var aaid: String? = null

    override fun extraParams(): Map<String, Any?> = linkedMapOf("userid" to userid, "aaid" to aaid)
}
