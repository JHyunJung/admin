package com.crosscert.fidoadmin.manager.web

import com.crosscert.fidoadmin.common.SearchForm

class ManagerSearchForm : SearchForm() {
    var userId: String? = null
    var userNm: String? = null
    var status: String? = null

    override fun extraParams(): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        m["userId"] = userId; m["userNm"] = userNm; m["status"] = status
        return m
    }
}
