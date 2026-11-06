package com.crosscert.fidoadmin.fido.web

import com.crosscert.fidoadmin.common.SearchForm

/** CRITERIA 검색 조건. COMPANY_IDX 가 없는 테이블이라 기반의 companyIdx 는 쓰이지 않는다. */
class CriteriaSearchForm : SearchForm() {
    var aaid: String? = null

    override fun extraParams(): Map<String, Any?> = linkedMapOf("aaid" to aaid)
}
