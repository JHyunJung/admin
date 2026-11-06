package com.crosscert.fidoadmin.fido.web

import com.crosscert.fidoadmin.common.SearchForm

class UserSearchForm : SearchForm() {
    var userid: String? = null
    var servicename: String? = null
    var aaid: String? = null
    var status: String? = null

    override fun extraParams(): Map<String, Any?> =
        linkedMapOf("userid" to userid, "servicename" to servicename, "aaid" to aaid, "status" to status)
}
