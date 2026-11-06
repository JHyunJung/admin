package com.crosscert.fidoadmin.log.web

import com.crosscert.fidoadmin.common.SearchForm

/**
 * CCFA_EXCEPTIONS 검색. CREATEDTIME 이 VARCHAR2(64) 라 base 의 fromDate/toDate 대신
 * 문자열 부분 일치(createdtime) 로 검색한다. 필드명은 엔티티 속성명(eType, eLevel) 과 같게 둔다.
 */
class ExceptionLogSearchForm : SearchForm() {
    var eType: String? = null
    var eLevel: String? = null
    var message: String? = null
    var createdtime: String? = null

    override fun extraParams(): Map<String, Any?> {
        val m = LinkedHashMap<String, Any?>()
        m["eType"] = eType; m["eLevel"] = eLevel; m["message"] = message; m["createdtime"] = createdtime
        return m
    }
}
