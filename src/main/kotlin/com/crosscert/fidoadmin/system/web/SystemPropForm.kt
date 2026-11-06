package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.common.javaTrim
import com.crosscert.fidoadmin.common.ByteSize
import com.crosscert.fidoadmin.system.SecretProps
import com.crosscert.fidoadmin.system.entity.CcfaSystemProp
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

/**
 * CCFA_SYSTEM_PROP 입력 폼. 식별자(propKey, companyIdx)는 등록 시에만 쓰이고 수정에서는 바뀌지 않는다.
 * PROP_KEY 는 CRUD 경로의 {id} 세그먼트 조합("{PROP_KEY}@{COMPANY_IDX}")으로 그대로 쓰이므로
 * URL·리다이렉트에 안전한 문자만 허용한다. companyIdx 는 고객사 select 가 사라져 폼에
 * 값이 없으므로 컨트롤러가 유효 테넌트로 채운다([toNewEntity]).
 */
class SystemPropForm {
    @field:NotBlank @field:ByteSize(max = 128)
    @field:Pattern(regexp = "[A-Za-z0-9._-]+", message = "키는 영문, 숫자, 마침표(.), 밑줄(_), 하이픈(-) 만 쓸 수 있습니다.")
    var propKey: String? = null
    @field:ByteSize(max = 4000) var propValue: String? = null
    @field:NotBlank @field:ByteSize(max = 20) var shareType: String? = "NO"

    /** 신규 엔티티. 식별자는 여기서만 채운다. companyIdx 는 유효 테넌트다(폼 값이 아니다). */
    fun toNewEntity(companyIdx: Long?): CcfaSystemProp {
        val p = CcfaSystemProp()
        p.id = CcfaSystemPropId(propKey?.javaTrim(), companyIdx)
        applyTo(p)
        return p
    }

    /**
     * 수정 가능한 값만 반영한다. 식별자는 건드리지 않는다.
     *
     * 비밀값은 폼이 마스크를 받아 돌아오므로, 그대로 저장하면 실제 값이 "********" 로
     * 덮어써진다. 입력이 있을 때만 바꾸고 아니면 기존 값을 둔다.
     */
    fun applyTo(p: CcfaSystemProp) {
        val id = p.id
        if (SecretProps.shouldSave(if (id == null) propKey else id.propKey, propValue)) {
            p.propValue = propValue
        }
        p.shareType = shareType
    }

    companion object {
        /** 수정 폼. 비밀값은 실제 값 대신 마스크를 채운다(textarea 에 그대로 실리면 소스로 읽힌다). */
        @JvmStatic
        fun from(p: CcfaSystemProp): SystemPropForm {
            val f = SystemPropForm()
            f.propKey = p.id!!.propKey
            f.propValue = SecretProps.forDisplay(f.propKey, p.propValue)
            f.shareType = p.shareType
            return f
        }
    }
}
