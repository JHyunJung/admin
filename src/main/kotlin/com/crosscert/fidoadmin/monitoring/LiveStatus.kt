package com.crosscert.fidoadmin.monitoring

/**
 * 고객사별 실시간 로그 상태. 선언 순서가 화면 정렬 순서다(문제가 큰 것이 위).
 *
 * @param label 화면에 보일 이름
 * @param badge 부트스트랩 배지 클래스
 */
enum class LiveStatus(val label: String, val badge: String) {
    DOWN("끊김", "text-bg-danger"),
    EXCEPTION_SPIKE("예외 급증", "text-bg-danger"),
    DROP("감소", "text-bg-warning"),
    OK("정상", "text-bg-success"),
    NONE("-", ""),
    UNKNOWN("확인 불가", "text-bg-secondary");

    companion object {
        /**
         * null = 그 값을 모른다(조회 실패·사용 안 함·기준일 없음).
         * 모르는 값으로는 그 값에 기대는 판정(끊김·감소)을 하지 않고, 아는 값(예외)만으로 가능한 판정은 한다.
         * 위에서부터 먼저 맞는 규칙이 이긴다.
         *
         * @param recent       최근 구간의 로그 건수
         * @param baseline     기준일의 같은 구간 건수
         * @param todayTotal   오늘 누적 건수
         * @param recentErrors 최근 구간의 예외 건수
         */
        fun judge(recent: Long?, baseline: Long?, todayTotal: Long?, recentErrors: Long?, p: MonitoringProperties): LiveStatus {
            val hasBaseline = recent != null && baseline != null && baseline >= p.minBaseline
            if (hasBaseline && recent == 0L) return DOWN
            if (recentErrors != null && recentErrors >= p.exceptionThreshold) return EXCEPTION_SPIKE
            if (hasBaseline && recent!! < baseline!! * p.dropRatio) return DROP
            if (recent == null || todayTotal == null) return UNKNOWN
            return if (todayTotal > 0) OK else NONE
        }
    }
}
