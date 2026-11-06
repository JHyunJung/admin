package com.crosscert.fidoadmin.log.web

import com.crosscert.fidoadmin.log.entity.CcfaExceptions

/** 목록 행. CLOB(EXCEPTION_DATA) 과 상세 메시지는 싣지 않는다. createdtime 은 원본대로 문자열. */
@JvmRecord
data class ExceptionLogRow(
    val idx: Long?, val companyIdx: Long?, val type: String?, val level: String?, val message: String?,
    val createdtime: String?,
) {
    companion object {
        @JvmStatic
        fun from(e: CcfaExceptions): ExceptionLogRow = ExceptionLogRow(e.idx, e.companyIdx, e.eType, e.eLevel,
            e.exceptionMessage, e.createdtime)
    }
}
