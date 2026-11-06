package com.crosscert.fidoadmin.fido.web

import com.crosscert.fidoadmin.common.SearchForm

/** SIGN 검색 조건. 기간은 기반의 fromDate/toDate(CREATETIME 기준). del 은 'Y'/'N'/빈값(전체). */
class SignSearchForm : SearchForm() {
    var userid: String? = null
    var del: String? = null

    override fun extraParams(): Map<String, Any?> = linkedMapOf("userid" to userid, "del" to del)
}
