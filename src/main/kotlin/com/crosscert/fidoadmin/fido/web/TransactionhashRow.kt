package com.crosscert.fidoadmin.fido.web

import com.crosscert.fidoadmin.fido.entity.Transactionhash
import java.time.LocalDateTime

/** TRANSACTIONHASH 목록 행. CLOB(CONTENT) 은 싣지 않는다. */
@JvmRecord
data class TransactionhashRow(
    val idx: Long?, val companyIdx: Long?, val userid: String?, val contenthash: String?, val createtime: LocalDateTime?,
) {
    companion object {
        @JvmStatic
        fun of(t: Transactionhash): TransactionhashRow =
            TransactionhashRow(t.idx, t.companyIdx, t.userid, t.contenthash, t.createtime)
    }
}
