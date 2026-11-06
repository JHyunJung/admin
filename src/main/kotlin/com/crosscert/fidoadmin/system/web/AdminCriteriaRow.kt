package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.system.entity.CcfaCriteria
import java.time.LocalDateTime

/** 목록용 DTO. CLOB(JSONDATA) 제외. */
@JvmRecord
data class AdminCriteriaRow(val idx: Long?, val aaid: String?, val metahash: String?, val updatedtime: LocalDateTime?) {
    companion object {
        @JvmStatic
        fun of(c: CcfaCriteria): AdminCriteriaRow = AdminCriteriaRow(c.idx, c.aaid, c.metahash, c.updatedtime)
    }
}
