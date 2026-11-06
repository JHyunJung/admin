package com.crosscert.fidoadmin.fido.web

import com.crosscert.fidoadmin.common.SearchForm

class AppserverSearchForm : SearchForm() {
    var memberCode: String? = null
    var memberId: String? = null
    var type: String? = null

    override fun extraParams(): Map<String, Any?> =
        linkedMapOf("memberCode" to memberCode, "memberId" to memberId, "type" to type)
}
