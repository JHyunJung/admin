package com.crosscert.fidoadmin.fido2.web

import com.crosscert.fidoadmin.common.SearchForm

class Fido2DemoAccessCodeSearchForm : SearchForm() {
    var accesscode: String? = null
    var vendorname: String? = null
    var status: String? = null

    override fun extraParams(): Map<String, Any?> =
        linkedMapOf("accesscode" to accesscode, "vendorname" to vendorname, "status" to status)
}
