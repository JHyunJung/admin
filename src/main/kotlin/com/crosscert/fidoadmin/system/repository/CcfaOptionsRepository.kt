package com.crosscert.fidoadmin.system.repository

import com.crosscert.fidoadmin.common.AdminRepository
import com.crosscert.fidoadmin.system.entity.CcfaOptions

interface CcfaOptionsRepository : AdminRepository<CcfaOptions, Long> {
    fun findByOptionIdxOrderByIdxAsc(optionIdx: Long?): List<CcfaOptions>
    fun countByOptionIdx(optionIdx: Long?): Long
}
