package com.crosscert.fidoadmin.fido2.web

import com.crosscert.fidoadmin.common.SearchForm

class Fido2MetadataSearchForm : SearchForm() {
    var aaguid: String? = null
    var description: String? = null
    var protocolfamily: String? = null

    override fun extraParams(): Map<String, Any?> =
        linkedMapOf("aaguid" to aaguid, "description" to description, "protocolfamily" to protocolfamily)
}
