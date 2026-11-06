package com.crosscert.fidoadmin.log.web

import com.crosscert.fidoadmin.common.SearchForm

/** CCFA_MAILING 검색: 상태, 수신자("TO" 컬럼 → 속성 to), SMS 상태. */
class MailingSearchForm : SearchForm() {
    var status: String? = null
    var to: String? = null
    var smsStatus: String? = null

    override fun extraParams(): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        m["status"] = status; m["to"] = to; m["smsStatus"] = smsStatus
        return m
    }
}
