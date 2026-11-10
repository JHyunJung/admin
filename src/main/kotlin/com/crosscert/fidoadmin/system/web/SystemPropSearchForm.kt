package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.common.SearchForm

/** 시스템 설정 검색: 키(부분 일치). 고객사는 유효 테넌트로 고정된다. */
class SystemPropSearchForm : SearchForm() {
    var propKey: String? = null

    override fun extraParams(): Map<String, Any?> = linkedMapOf("propKey" to propKey)
}
