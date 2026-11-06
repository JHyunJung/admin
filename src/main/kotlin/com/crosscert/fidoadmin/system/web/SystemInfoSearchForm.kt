package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.common.SearchForm

class SystemInfoSearchForm : SearchForm() {
    var propKey: String? = null

    override fun extraParams(): Map<String, Any?> = linkedMapOf("propKey" to propKey)
}
