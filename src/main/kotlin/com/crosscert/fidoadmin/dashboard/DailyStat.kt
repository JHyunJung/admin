package com.crosscert.fidoadmin.dashboard

import java.time.LocalDate

@JvmRecord
data class DailyStat(
    val date: LocalDate, val authS: Long, val authF: Long, val tcS: Long, val tcF: Long,
    val regS: Long, val regF: Long, val deregS: Long, val deregF: Long,
)
