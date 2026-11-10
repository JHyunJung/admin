package com.crosscert.fidoadmin.fido.web

import com.crosscert.fidoadmin.common.SearchForm

/**
 * 사용자 단위 목록(/users)의 검색 조건. userid·servicename 만 받는다.
 * AAID·상태 조건은 없다 — 한 사람의 기기마다 값이 다를 수 있어 묶음 행의 조건으로 맞지 않는다.
 * AAID·상태로 기기를 찾는 화면도 지금은 없다(기기 목록 /users/{userid}/credentials 는
 * userid·servicename 만 받는다).
 */
class UserAccountSearchForm : SearchForm() {
    var userid: String? = null
    var servicename: String? = null

    override fun extraParams(): Map<String, Any?> =
        linkedMapOf("userid" to userid, "servicename" to servicename)
}
