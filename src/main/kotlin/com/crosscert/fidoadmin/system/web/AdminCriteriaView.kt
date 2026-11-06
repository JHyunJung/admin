package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.common.JsonPretty
import com.crosscert.fidoadmin.system.entity.CcfaCriteria
import java.time.LocalDateTime

/** 상세용 DTO. JSONDATA 는 정리해서 보여준다. */
@JvmRecord
data class AdminCriteriaView(
    val idx: Long?,
    val aaid: String?,
    val metahash: String?,
    val jsondataPretty: String?,
    val createtime: LocalDateTime?,
    val updatedtime: LocalDateTime?,
) {
    companion object {
        @JvmStatic
        fun of(c: CcfaCriteria): AdminCriteriaView = AdminCriteriaView(
            c.idx, c.aaid, c.metahash, JsonPretty.pretty(c.jsondata),
            c.createtime, c.updatedtime,
        )
    }
}
