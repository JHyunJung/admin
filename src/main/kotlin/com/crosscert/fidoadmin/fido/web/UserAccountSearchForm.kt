package com.crosscert.fidoadmin.fido.web

import com.crosscert.fidoadmin.common.SearchForm

/**
 * 사용자 단위 목록의 검색 조건. 기기 단위 화면([UserSearchForm])과 달리
 * AAID·상태가 없다 — 한 사람의 기기마다 값이 다를 수 있어 묶음 행의 조건으로 맞지 않는다.
 * 그 조건으로 찾으려면 기기 목록 화면을 쓴다.
 */
class UserAccountSearchForm : SearchForm() {
    var userid: String? = null
    var servicename: String? = null

    override fun extraParams(): Map<String, Any?> =
        linkedMapOf("userid" to userid, "servicename" to servicename)
}
