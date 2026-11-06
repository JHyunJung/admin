package com.crosscert.fidoadmin.company.web

import com.crosscert.fidoadmin.common.SearchForm

class CompanySearchForm : SearchForm() {
    var companyName: String? = null
    var companyType: String? = null
    var enableType: String? = null

    override fun extraParams(): Map<String, Any?> =
        linkedMapOf("companyName" to companyName, "companyType" to companyType, "enableType" to enableType)
}
