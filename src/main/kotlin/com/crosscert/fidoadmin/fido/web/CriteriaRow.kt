package com.crosscert.fidoadmin.fido.web

import com.crosscert.fidoadmin.fido.entity.Criteria
import java.time.LocalDateTime

/** AAID(정책) 목록 행. CLOB(JSONDATA) 은 싣지 않는다. */
@JvmRecord
data class CriteriaRow(
    val idx: Long?,
    val aaid: String?,
    val vendorids: String?,
    val userverification: Long?,
    val keyprotection: Long?,
    val matcherprotection: Long?,
    val authenticatorversion: Long?,
    val metahash: String?,
    val updatedtime: LocalDateTime?,
    val assertionschemes: String?,
    val createtime: LocalDateTime?,
) {
    companion object {
        @JvmStatic
        fun of(c: Criteria): CriteriaRow = CriteriaRow(
            c.idx,
            c.aaid,
            c.vendorids,
            c.userverification,
            c.keyprotection,
            c.matcherprotection,
            c.authenticatorversion,
            c.metahash,
            c.updatedtime,
            c.assertionschemes,
            c.createtime,
        )
    }
}
