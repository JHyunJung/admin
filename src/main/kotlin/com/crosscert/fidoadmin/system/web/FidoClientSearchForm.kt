package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.common.SearchForm

class FidoClientSearchForm : SearchForm() {
    var servercode: String? = null
    var status: String? = null

    override fun extraParams(): Map<String, Any?> = linkedMapOf("servercode" to servercode, "status" to status)
}
