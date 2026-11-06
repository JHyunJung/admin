package com.crosscert.fidoadmin.fido.web

import com.crosscert.fidoadmin.fido.entity.Userinfo
import java.time.LocalDateTime

/** USERINFO 목록 행. PUBKEY/CERTIFICATE 는 싣지 않는다. */
@JvmRecord
data class UserRow(
    val idx: Long?, val companyIdx: Long?, val servicename: String?, val userid: String?, val aaid: String?,
    val status: String?, val signcounter: Long?, val regtime: LocalDateTime?,
) {
    companion object {
        @JvmStatic
        fun from(u: Userinfo): UserRow =
            UserRow(u.idx, u.companyIdx, u.servicename, u.userid, u.aaid, u.status, u.signcounter, u.regtime)
    }
}
