package com.crosscert.fidoadmin.fido2.web

import com.crosscert.fidoadmin.common.SearchForm

class Fido2CredentialParamsSearchForm : SearchForm() {
    var credType: String? = null
    var status: String? = null

    override fun extraParams(): Map<String, Any?> = linkedMapOf("credType" to credType, "status" to status)
}
