package com.crosscert.fidoadmin.monitoring

import java.time.LocalDate
import java.time.LocalDateTime

/** 한 열(로그·예외)의 읽기 상태. 화면은 OK 가 아니면 값 대신 "사용 안 함"(DISABLED)·"확인 불가"(FAILED)를 쓴다. */
enum class ColumnState { OK, DISABLED, FAILED }

/**
 * 고객사 한 줄. null 은 그 값을 모른다는 뜻이다(조회 실패·사용 안 함·기준일 없음). 0 과 구분한다.
 *
 * @param recent       최근 60분 로그 건수
 * @param baseline     기준일의 같은 60분 로그 건수
 * @param todayTotal   오늘 0시부터의 로그 건수
 * @param lastLog      오늘 마지막 로그 시각. 오늘 로그가 없으면 null
 * @param recentErrors 최근 60분 ERROR 예외 건수
 * @param todayErrors  오늘 ERROR 예외 건수
 */
data class LiveRow(
    val companyIdx: Long,
    val name: String,
    val status: LiveStatus,
    val recent: Long?,
    val baseline: Long?,
    val todayTotal: Long?,
    val lastLog: LocalDateTime?,
    val recentErrors: Long?,
    val todayErrors: Long?,
)

/** 고객사 줄로 갈 수 없는 예외(COMPANY_IDX 0·NULL·목록에 없음)의 합. 시스템 장애가 여기 모인다. */
data class CommonRow(val recentErrors: Long, val todayErrors: Long, val spike: Boolean)

/** @param prev 직전 주 합계. 직전 주에 통계가 없으면 null */
data class WeekRow(val companyIdx: Long, val name: String, val cur: WeekTotals, val prev: WeekTotals?, val dropped: Boolean)

/** 최근 주 [from, to](양끝 포함)와 그 직전 주의 고객사별 비교. */
data class WeekTable(val from: LocalDate, val to: LocalDate, val rows: List<WeekRow>)

/**
 * SUPER 관제 화면 한 장의 모델.
 *
 * @param asOf         판정 기준 시각(분으로 내림)
 * @param cacheSeconds 당일 로그를 보관하는 시간. 화면에 "최대 N초 전 값"으로 알린다
 * @param baselineDate 비교에 쓴 날짜. null = 기준일 없음(끊김·감소를 판정하지 않았다)
 * @param common       공통 예외 줄. 예외를 모르면 null
 * @param staleNotice  통계 집계 멈춤 알림. null = 알림 없음
 * @param week         주간 비교 표. null = 통계 없음
 */
data class MonitoringBoard(
    val asOf: LocalDateTime,
    val cacheSeconds: Long,
    val logs: ColumnState,
    val exceptions: ColumnState,
    val baselineDate: LocalDate?,
    val common: CommonRow?,
    val live: List<LiveRow>,
    val staleNotice: String?,
    val week: WeekTable?,
)
