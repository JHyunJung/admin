package com.crosscert.fidoadmin.fido.web

import com.crosscert.fidoadmin.common.SearchForm

/** CHALLENGE 검색 조건. 기간은 기반의 fromDate/toDate(CREATETIME 기준). */
class ChallengeSearchForm : SearchForm() {
    var userid: String? = null
    var servicename: String? = null

    override fun extraParams(): Map<String, Any?> =
        linkedMapOf("userid" to userid, "servicename" to servicename)
}
