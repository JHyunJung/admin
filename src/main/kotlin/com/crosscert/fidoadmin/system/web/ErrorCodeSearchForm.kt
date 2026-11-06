package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.common.SearchForm

class ErrorCodeSearchForm : SearchForm() {
    var errorCode: String? = null
    var errorType: String? = null

    override fun extraParams(): Map<String, Any?> = linkedMapOf("errorCode" to errorCode, "errorType" to errorType)
}
