package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.common.SearchForm

class FieldSearchForm : SearchForm() {
    var fieldTable: String? = null
    var fieldName: String? = null

    override fun extraParams(): Map<String, Any?> = linkedMapOf("fieldTable" to fieldTable, "fieldName" to fieldName)
}
