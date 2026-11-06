package com.crosscert.fidoadmin.fido.web

import com.crosscert.fidoadmin.common.JsonPretty
import com.crosscert.fidoadmin.fido.entity.Criteria
import java.time.LocalDateTime

/** CRITERIA 상세. 17개 컬럼을 모두 담되 JSONDATA 는 정리된 문자열(jsondataPretty)로 준다. */
@JvmRecord
data class CriteriaView(
    val idx: Long?, val aaid: String?, val vendorids: String?, val userverification: Long?, val keyprotection: Long?,
    val matcherprotection: Long?, val attachmenthnumber: Long?, val tcdisplay: Long?, val tcdisplaycontenttype: String?,
    val authenticationalgorithms: String?, val assertionschemes: String?, val attestationtypes: String?,
    val authenticatorversion: Long?, val metahash: String?, val jsondataPretty: String?,
    val createtime: LocalDateTime?, val updatedtime: LocalDateTime?,
) {
    companion object {
        @JvmStatic
        fun of(c: Criteria): CriteriaView = CriteriaView(
            c.idx, c.aaid, c.vendorids, c.userverification, c.keyprotection,
            c.matcherprotection, c.attachmenthnumber, c.tcdisplay, c.tcdisplaycontenttype,
            c.authenticationalgorithms, c.assertionschemes, c.attestationtypes,
            c.authenticatorversion, c.metahash, JsonPretty.pretty(c.jsondata),
            c.createtime, c.updatedtime,
        )
    }
}
