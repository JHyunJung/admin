package com.crosscert.fidoadmin.manager.web

import com.crosscert.fidoadmin.manager.entity.CcfaManager
import java.time.LocalDateTime

/** 목록 행. USER_PW 제외. Java record 였으므로 `@JvmRecord` 로 같은 접근자(`idx()` 등)를 유지한다. */
@JvmRecord
data class ManagerRow(
    val idx: Long?, val userId: String?, val userNm: String?, val userEmail: String?, val companyIdx: Long?,
    val status: String?, val login: String?, val lastAccess: LocalDateTime?,
) {
    companion object {
        @JvmStatic
        fun of(m: CcfaManager): ManagerRow = ManagerRow(
            m.idx, m.userId, m.userNm, m.userEmail, m.companyIdx,
            m.status, m.login, m.lastAccess,
        )
    }
}
