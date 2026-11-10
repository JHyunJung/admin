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
            var authS = 0L; var authF = 0L; var tcS = 0L; var tcF = 0L
            var regS = 0L; var regF = 0L; var deregS = 0L; var deregF = 0L
            for (r in rows) {
                authS += r.authS; authF += r.authF; tcS += r.tcS; tcF += r.tcF
                regS += r.regS; regF += r.regF; deregS += r.deregS; deregF += r.deregF
            }
            return StatTotals(
                authS = authS, authF = authF, tcS = tcS, tcF = tcF,
                regS = regS, regF = regF, deregS = deregS, deregF = deregF)
        }
    }
}
