package com.crosscert.fidoadmin.fido.web

import com.crosscert.fidoadmin.fido.entity.TransactionConfirmation
import java.time.LocalDateTime

/** TRANSACTION_CONFIRMATION 목록 행. CLOB(CONTENT) 은 싣지 않는다. */
@JvmRecord
data class TransactionConfirmationRow(
    val idx: Long?, val companyIdx: Long?, val userid: String?, val aaid: String?, val contenttype: String?,
    val createdtime: LocalDateTime?,
) {
    companion object {
        @JvmStatic
        fun of(t: TransactionConfirmation): TransactionConfirmationRow =
            TransactionConfirmationRow(t.idx, t.companyIdx, t.userid, t.aaid, t.contenttype, t.createdtime)
    }
}
