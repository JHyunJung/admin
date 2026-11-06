package com.crosscert.fidoadmin.fido.repository

import com.crosscert.fidoadmin.common.AdminRepository
import com.crosscert.fidoadmin.fido.entity.Appserver

interface AppserverRepository : AdminRepository<Appserver, Long> {
    fun existsByCompanyIdxAndMemberCodeAndMemberId(companyIdx: Long?, memberCode: String?, memberId: String?): Boolean
    fun existsByCompanyIdxAndMemberCodeAndMemberIdAndIdxNot(companyIdx: Long?, memberCode: String?, memberId: String?, idx: Long?): Boolean
    fun existsByMemberCode(memberCode: String?): Boolean
}
