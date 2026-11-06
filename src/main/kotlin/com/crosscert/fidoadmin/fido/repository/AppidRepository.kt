package com.crosscert.fidoadmin.fido.repository

import com.crosscert.fidoadmin.common.AdminRepository
import com.crosscert.fidoadmin.fido.entity.Appid

interface AppidRepository : AdminRepository<Appid, Long> {
    fun countByCompanyIdx(companyIdx: Long?): Long
}
