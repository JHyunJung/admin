package com.crosscert.fidoadmin.fido.web

import com.crosscert.fidoadmin.common.SearchForm

class AppidSearchForm : SearchForm() {
    var appid: String? = null
    var servicename: String? = null
    var status: String? = null

    override fun extraParams(): Map<String, Any?> =
        linkedMapOf("appid" to appid, "servicename" to servicename, "status" to status)
}
