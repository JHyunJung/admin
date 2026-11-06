package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.common.SearchForm

class OptionSearchForm : SearchForm() {
    var optionName: String? = null

    override fun extraParams(): Map<String, Any?> = linkedMapOf("optionName" to optionName)
}
