package com.crosscert.fidoadmin.log.web

import com.crosscert.fidoadmin.log.entity.CcfaExceptions

/** 상세. 8컬럼 전부. 속성명은 뷰에서 다루기 쉽게 정리한다(eType → type 등). */
@JvmRecord
data class ExceptionLogView(
    val idx: Long?, val companyIdx: Long?, val type: String?, val level: String?, val message: String?,
    val detailMessage: String?, val data: String?, val createdtime: String?,
) {
    companion object {
        @JvmStatic
        fun from(e: CcfaExceptions): ExceptionLogView = ExceptionLogView(e.idx, e.companyIdx, e.eType, e.eLevel,
            e.exceptionMessage, e.exceptionDetailMessage, e.exceptionData, e.createdtime)
    }
}
