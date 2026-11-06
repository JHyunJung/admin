package com.crosscert.fidoadmin.fido.web

import com.crosscert.fidoadmin.fido.entity.Userinfo
import java.time.LocalDateTime

/**
 * USERINFO 상세. PUBKEY/CERTIFICATE 는 앞 16자만. 전체 값은 어떤 경로로도 뷰에 가지 않는다.
 *
 * KEYID 도 같이 자른다. 민감 컬럼으로 꼽힌 것은 아니지만 크리덴셜 식별자(1024자)이고,
 * 바로 옆의 PUBKEY 를 자르면서 이것만 통째로 내보내는 것은 앞뒤가 맞지 않는다.
 */
@JvmRecord
data class UserView(
    val idx: Long?, val companyIdx: Long?, val bioType: Long?, val servicename: String?, val userid: String?, val aaid: String?,
    val authenticatorversion: Long?, val keyidHead: String?, val pubkeyHead: String?, val certificateHead: String?,
    val signcounter: Long?, val uvs: String?, val status: String?, val regtime: LocalDateTime?, val createdtime: LocalDateTime?,
) {
    companion object {
        @JvmStatic
        fun from(u: Userinfo): UserView = UserView(
            u.idx, u.companyIdx, u.bioType, u.servicename, u.userid, u.aaid,
            u.authenticatorversion, head16(u.keyid), head16(u.pubkey), head16(u.certificate),
            u.signcounter, u.uvs, u.status, u.regtime, u.createdtime,
        )

        /** 앞 16자. null 은 null. */
        @JvmStatic
        fun head16(s: String?): String? {
            if (s == null) return null
            return if (s.length <= 16) s else s.substring(0, 16)
        }
    }
}
