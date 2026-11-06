package com.crosscert.fidoadmin.fido.web

import com.crosscert.fidoadmin.common.SearchForm

/** TRANSACTIONHASH 검색 조건. 기간은 기반의 fromDate/toDate(CREATETIME 기준). */
class TransactionhashSearchForm : SearchForm() {
    var userid: String? = null

    override fun extraParams(): Map<String, Any?> = linkedMapOf("userid" to userid)
}
