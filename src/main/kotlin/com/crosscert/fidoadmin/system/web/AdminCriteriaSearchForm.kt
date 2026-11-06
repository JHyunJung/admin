package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.common.SearchForm

class AdminCriteriaSearchForm : SearchForm() {
    var aaid: String? = null

    override fun extraParams(): Map<String, Any?> = linkedMapOf("aaid" to aaid)
}
