package com.crosscert.fidoadmin.system.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.io.Serializable

/**
 * CCFA_SYSTEM_PROP 의 복합키(PROP_KEY, COMPANY_IDX).
 *
 * Lombok `@EqualsAndHashCode` 와 같은 의미로 두 필드로 equals/hashCode 를 손으로 구현한다
 * (data class 는 쓰지 않는다 — JPA 가 기본 생성자·setter 를 쓴다).
 */
@Embeddable
class CcfaSystemPropId() : Serializable {
    @Column(name = "PROP_KEY", length = 128) var propKey: String? = null
    @Column(name = "COMPANY_IDX") var companyIdx: Long? = null

    constructor(propKey: String?, companyIdx: Long?) : this() {
        this.propKey = propKey
        this.companyIdx = companyIdx
    }

    /** URL 경로 한 조각으로 쓰는 표현. "{PROP_KEY}@{COMPANY_IDX}". */
    fun toPathValue(): String = "$propKey@$companyIdx"

    /** CrudController 가 리다이렉트 경로를 "basePath/" + id 로 만들므로 경로 값과 같게 둔다. */
    override fun toString(): String = toPathValue()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CcfaSystemPropId) return false
        return propKey == other.propKey && companyIdx == other.companyIdx
    }

    override fun hashCode(): Int {
        // Lombok @EqualsAndHashCode 와 같은 계산(PRIME 59, null 은 43).
        var result = 1
        result = result * 59 + (propKey?.hashCode() ?: 43)
        result = result * 59 + (companyIdx?.hashCode() ?: 43)
        return result
    }

    companion object {
        private const val serialVersionUID: Long = 1L

        /**
         * 경로 값 → 복합키. 키에 '@' 가 들어갈 수 있으므로 마지막 '@' 기준으로 나눈다.
         * 형식이 틀리면 IllegalArgumentException (컨트롤러에서는 404 로 끝난다).
         */
        @JvmStatic
        fun parse(value: String?): CcfaSystemPropId {
            if (value == null) throw IllegalArgumentException("시스템 설정 키가 없습니다")
            val at = value.lastIndexOf('@')
            if (at <= 0 || at == value.length - 1) {
                throw IllegalArgumentException("시스템 설정 키 형식이 아닙니다: $value")
            }
            val key = value.substring(0, at)
            try {
                return CcfaSystemPropId(key, value.substring(at + 1).toLong())
            } catch (e: NumberFormatException) {
                throw IllegalArgumentException("시스템 설정 키 형식이 아닙니다: $value", e)
            }
        }
    }
}
