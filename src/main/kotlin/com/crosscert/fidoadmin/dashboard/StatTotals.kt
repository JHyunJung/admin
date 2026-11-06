package com.crosscert.fidoadmin.dashboard

@JvmRecord
data class StatTotals(
    val authS: Long, val authF: Long, val tcS: Long, val tcF: Long,
    val regS: Long, val regF: Long, val deregS: Long, val deregF: Long,
) {

    fun authTotal(): Long = authS + authF

    /** 소수 첫째 자리까지. 분모 0 이면 0. */
    fun authSuccessRate(): Double =
        if (authTotal() == 0L) 0.0 else Math.round(authS * 1000.0 / authTotal()) / 10.0

    companion object {
        @JvmStatic
        fun of(rows: List<DailyStat>): StatTotals {
            val s = LongArray(8)
            for (r in rows) {
                s[0] += r.authS; s[1] += r.authF; s[2] += r.tcS; s[3] += r.tcF
                s[4] += r.regS; s[5] += r.regF; s[6] += r.deregS; s[7] += r.deregF
            }
            return StatTotals(s[0], s[1], s[2], s[3], s[4], s[5], s[6], s[7])
        }
    }
}
