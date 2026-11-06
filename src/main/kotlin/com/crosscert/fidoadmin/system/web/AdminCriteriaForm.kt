package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.common.javaIsBlank
import com.crosscert.fidoadmin.common.ByteSize
import com.crosscert.fidoadmin.system.entity.CcfaCriteria
import jakarta.validation.constraints.NotBlank

/** CCFA_CRITERIA 입력 폼. JSONDATA 는 CLOB 이라 길이 제한이 없고 형식만 컨트롤러가 검증한다. */
class AdminCriteriaForm {
    @field:NotBlank @field:ByteSize(max = 64) var aaid: String? = null
    @field:ByteSize(max = 512) var metahash: String? = null
    var jsondata: String? = null

    /** 식별자(IDX)는 시퀀스가 채우므로 폼이 건드리지 않는다. */
    fun toNewEntity(): CcfaCriteria {
        val c = CcfaCriteria()
        applyTo(c)
        return c
    }

    fun applyTo(c: CcfaCriteria) {
        c.aaid = aaid; c.metahash = metahash
        val json = jsondata
        c.jsondata = if (json == null || json.javaIsBlank()) null else json // EMPTY_CLOB 과 null 을 같게 본다
    }

    companion object {
        @JvmStatic
        fun from(c: CcfaCriteria): AdminCriteriaForm = AdminCriteriaForm().apply {
            aaid = c.aaid; metahash = c.metahash; jsondata = c.jsondata
        }
    }
}
