package com.crosscert.fidoadmin.log.web

import com.crosscert.fidoadmin.common.SearchForm

class AuditLogSearchForm : SearchForm() {
    var userId: String? = null
    var type: String? = null
    var message: String? = null

    override fun extraParams(): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        m["userId"] = userId; m["type"] = type; m["message"] = message
        return m
    }
}
