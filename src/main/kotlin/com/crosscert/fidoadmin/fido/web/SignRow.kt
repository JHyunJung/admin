package com.crosscert.fidoadmin.fido.web

import com.crosscert.fidoadmin.fido.entity.Sign
import java.time.LocalDateTime

/** SIGN 목록 행. CLOB(PLAINTEXT)·DATA·SIGNATURE 는 싣지 않고 ASSERTION 은 앞 32자만 보여준다. */
@JvmRecord
data class SignRow(
    val idx: Long?, val companyIdx: Long?, val userid: String?, val dn: String?, val memo: String?, val del: String?,
    val createtime: LocalDateTime?, val assertionHead: String?,
) {
    companion object {
        internal const val HEAD = 32

        @JvmStatic
        fun of(s: Sign): SignRow {
            val a = s.assertion
            val head = if (a == null) null else if (a.length <= HEAD) a else a.substring(0, HEAD) + "…"
            return SignRow(s.idx, s.companyIdx, s.userid, s.dn, s.memo, s.del, s.createtime, head)
        }
    }
}
