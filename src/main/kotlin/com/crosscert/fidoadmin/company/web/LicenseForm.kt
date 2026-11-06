package com.crosscert.fidoadmin.company.web

import com.crosscert.fidoadmin.common.ByteSize
import com.crosscert.fidoadmin.company.entity.CcfaLicense

/**
 * CCFA_LICENSE 입력 폼. COMPANY_NAME 은 폼에 없다(서비스가 동기화).
 *
 * companyIdx 는 폼에서 고르지 않는다 — 세션이 정한 유효 테넌트를
 * `CrudService.create()`/`update()` 가 덮어쓴다. 그래서 여기 `@NotNull` 을
 * 두면 폼에 값이 실려 오지 않는 모든 요청이 `@Valid` 단계에서 막혀, 등록 자체가 불가능해진다.
 */
class LicenseForm {
    var companyIdx: Long? = null
    @field:ByteSize(max = 128) var contactName: String? = null
    @field:ByteSize(max = 128) var contactPhone: String? = null
    @field:ByteSize(max = 256) var contactEmail: String? = null
    @field:ByteSize(max = 256) var serviceName: String? = null
    @field:ByteSize(max = 1024) var etc: String? = null
    @field:ByteSize(max = 2048) var license: String? = null
    @field:ByteSize(max = 256) var filePath: String? = null
    @field:ByteSize(max = 256) var hashvalue: String? = null

    fun applyTo(l: CcfaLicense) {
        l.companyIdx = companyIdx; l.contactName = contactName; l.contactPhone = contactPhone
        l.contactEmail = contactEmail; l.serviceName = serviceName; l.etc = etc
        l.license = license; l.filePath = filePath; l.hashvalue = hashvalue
    }

    companion object {
        @JvmStatic
        fun from(l: CcfaLicense): LicenseForm = LicenseForm().apply {
            companyIdx = l.companyIdx; contactName = l.contactName; contactPhone = l.contactPhone
            contactEmail = l.contactEmail; serviceName = l.serviceName; etc = l.etc
            license = l.license; filePath = l.filePath; hashvalue = l.hashvalue
        }
    }
}
