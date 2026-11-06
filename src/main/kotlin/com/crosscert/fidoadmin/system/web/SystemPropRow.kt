package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.system.SecretProps
import com.crosscert.fidoadmin.system.entity.CcfaSystemProp
import java.time.LocalDateTime

/**
 * 목록·상세 출력 DTO. pathValue 가 링크에 쓰인다.
 *
 * 비밀값(SMTP_PASSWORD 등)은 값 대신 마스크를 싣는다. 이 화면은 임의 키를 직접 다루는
 * 저수준 화면이라, `/system/settings` 만 가리면 같은 행을 여기서 그대로 읽을 수 있다.
 */
@JvmRecord
data class SystemPropRow(
    val propKey: String?,
    val companyIdx: Long?,
    val propValue: String?,
    val shareType: String?,
    val updatedtime: LocalDateTime?,
    val pathValue: String,
) {
    companion object {
        @JvmStatic
        fun of(p: CcfaSystemProp): SystemPropRow {
            val id = p.id!!
            val key = id.propKey
            return SystemPropRow(
                key, id.companyIdx,
                SecretProps.forDisplay(key, p.propValue),
                p.shareType, p.updatedtime, id.toPathValue(),
            )
        }
    }
}
