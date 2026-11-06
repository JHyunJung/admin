package com.crosscert.fidoadmin.company.web

import com.crosscert.fidoadmin.common.ByteSize
import com.crosscert.fidoadmin.company.entity.CcfaCompany
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime
import org.springframework.format.annotation.DateTimeFormat

/** CCFA_COMPANY 입력 폼. 길이 제한은 DB 컬럼 길이. */
class CompanyForm {
    @field:NotBlank @field:ByteSize(max = 256) var companyName: String? = null
    @field:ByteSize(max = 20) var companyType: String? = null
    @field:ByteSize(max = 20) var vendorCode: String? = null
    @field:ByteSize(max = 512) var contact: String? = null
    @field:ByteSize(max = 50) var contactPhone: String? = null
    @field:ByteSize(max = 50) var contactPhone2: String? = null
    @field:ByteSize(max = 2048) var contactAddr: String? = null
    @field:NotBlank @field:ByteSize(max = 20) var enableType: String? = "Y"
    // HTML 의 <input type="datetime-local"> 값 형식은 "yyyy-MM-ddTHH:mm" (초·오프셋 없음)이라
    // ISO.DATE_TIME(오프셋 포함) 으로는 바인딩이 실패한다. 화면 입력 형식에 맞춘 패턴을 쓴다.
    @field:DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") var starttime: LocalDateTime? = null
    @field:DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") var endtime: LocalDateTime? = null
    @field:NotNull @field:Min(0) var maxAppid: Long? = 0L
    @field:NotNull @field:Min(0) var maxAppserver: Long? = 0L
    @field:NotNull @field:Min(0) var maxUser: Long? = 0L
    @field:ByteSize(max = 4000) var etc: String? = null

    fun applyTo(c: CcfaCompany) {
        c.companyName = companyName; c.companyType = companyType; c.vendorCode = vendorCode
        c.contact = contact; c.contactPhone = contactPhone; c.contactPhone2 = contactPhone2
        c.contactAddr = contactAddr; c.enableType = enableType; c.starttime = starttime
        c.endtime = endtime; c.maxAppid = maxAppid; c.maxAppserver = maxAppserver
        c.maxUser = maxUser; c.etc = etc
    }

    companion object {
        @JvmStatic
        fun from(c: CcfaCompany): CompanyForm = CompanyForm().apply {
            companyName = c.companyName; companyType = c.companyType; vendorCode = c.vendorCode
            contact = c.contact; contactPhone = c.contactPhone; contactPhone2 = c.contactPhone2
            contactAddr = c.contactAddr; enableType = c.enableType; starttime = c.starttime
            endtime = c.endtime; maxAppid = c.maxAppid; maxAppserver = c.maxAppserver
            maxUser = c.maxUser; etc = c.etc
        }
    }
}
