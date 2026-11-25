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
 * 로그 값은 셋으로 나눠 상태를 둔다. 최근 구간과 기준 구간은 자정 직후 전날 테이블까지 읽어야 해서
 * 당일([logs])을 읽었어도 따로 실패할 수 있다. 모든 줄이 같은 구간을 쓰므로 상태는 화면에 하나씩이면 된다.
 *
 * @param asOf         판정 기준 시각(분으로 내림)
 * @param cacheSeconds 당일 로그를 보관하는 시간. 화면에 "최대 N초 전 값"으로 알린다
 * @param logs         당일 로그(오늘 누계·마지막 로그)의 상태
 * @param recentLogs   최근 구간의 상태. FAILED = 구간이 걸친 전날을 읽지 못했다
 * @param baselineLogs 기준 구간의 상태. OK 인데 [baselineDate] 가 null 이면 기준일이 정말 없다.
 *                     FAILED = 기준일 후보를 읽지 못해 정하지 못했다
 * @param baselineDate 비교에 쓴 날짜. null = 기준일을 쓰지 않았다(끊김·감소를 판정하지 않았다)
 * @param common       공통 예외 줄. 예외를 모르면 null(화면에는 줄을 두고 칸에 이유를 쓴다)
 * @param staleNotice  통계 집계 멈춤 알림. null = 알림 없음
 * @param week         주간 비교 표. null = 통계 없음
 */
data class MonitoringBoard(
    val asOf: LocalDateTime,
    val cacheSeconds: Long,
    val logs: ColumnState,
    val recentLogs: ColumnState,
    val baselineLogs: ColumnState,
    val exceptions: ColumnState,
    val baselineDate: LocalDate?,
    val common: CommonRow?,
    val live: List<LiveRow>,
    val staleNotice: String?,
    val week: WeekTable?,
)
