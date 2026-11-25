package com.crosscert.fidoadmin.monitoring

/**
 * 한 주의 인증·등록·해지 합계. 비율은 소수 첫째 자리까지 반올림하고, 분모가 0 이면 null 이다.
 */
data class WeekTotals(val authS: Long, val authF: Long, val regS: Long, val regF: Long, val deregS: Long) {

    fun authTotal(): Long = authS + authF

    fun authRate(): Double? = rate(authS, authTotal())

    fun regRate(): Double? = rate(regS, regS + regF)

    /** 순증감: 등록 성공에서 해지를 뺀다. */
    fun netReg(): Long = regS - deregS

    /** 지난주 대비 인증량 변화율(%). 지난주가 없거나 0 이면 null. */
    fun authChangePct(prev: WeekTotals?): Double? {
        val before = prev?.authTotal()
        if (before == null || before == 0L) return null
        return round((authTotal() - before) * 100.0 / before)
    }

    /** 지난주 대비 인증 성공률 변화(%p). 어느 한쪽 비율이 null 이면 null. */
    fun authRatePointChange(prev: WeekTotals?): Double? {
        val now = authRate() ?: return null
        val before = prev?.authRate() ?: return null
        return round(now - before)
    }

    /** 인증량이 [MonitoringProperties.minAuth] 이상이고 성공률이 기준 %p 이상 내려갔는가. */
    fun dropped(prev: WeekTotals?, p: MonitoringProperties): Boolean {
        if (authTotal() < p.minAuth) return false
        val change = authRatePointChange(prev) ?: return false
        return change <= -p.successDropPoints
    }

    private companion object {
        fun rate(num: Long, den: Long): Double? = if (den == 0L) null else round(num * 100.0 / den)

        // StatTotals.authSuccessRate 와 같은 반올림
        fun round(x: Double): Double = Math.round(x * 10) / 10.0
    }
}
