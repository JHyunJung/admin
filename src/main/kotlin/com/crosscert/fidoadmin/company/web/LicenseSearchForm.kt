package com.crosscert.fidoadmin.company.web

import com.crosscert.fidoadmin.common.SearchForm

class LicenseSearchForm : SearchForm() {
    var serviceName: String? = null

    override fun extraParams(): Map<String, Any?> = linkedMapOf("serviceName" to serviceName)
}
