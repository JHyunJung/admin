package com.crosscert.fidoadmin.manager.web

import com.crosscert.fidoadmin.manager.entity.CcfaManager
import java.time.LocalDateTime

/** 상세. 16개 컬럼 중 USER_PW 만 제외한다. Java record 였으므로 `@JvmRecord` 로 같은 접근자를 유지한다. */
@JvmRecord
data class ManagerView(
    val idx: Long?, val userId: String?, val userNm: String?, val userEmail: String?, val userPhone: String?,
    val companyIdx: Long?, val status: String?, val login: String?, val blockTime: LocalDateTime?,
    val lastAccess: LocalDateTime?, val etc: String?, val alramType: String?, val alramLevel: String?,
    val createdtime: LocalDateTime?, val updatedtime: LocalDateTime?,
) {
    companion object {
        @JvmStatic
        fun of(m: CcfaManager): ManagerView = ManagerView(
            m.idx, m.userId, m.userNm, m.userEmail, m.userPhone, m.companyIdx,
            m.status, m.login, m.blockTime, m.lastAccess, m.etc,
            m.alramType, m.alramLevel, m.createdtime, m.updatedtime,
        )
    }
}
