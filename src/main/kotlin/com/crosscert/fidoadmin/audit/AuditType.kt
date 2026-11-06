package com.crosscert.fidoadmin.audit

/**
 * 감사 사건 종류. CCFA_AUDIT_LOG.TYPE 에 이름 그대로 저장된다.
 *
 * MENU_VIEW/DATA_VIEW 는 조회 기록이다. 변경 사건과 달리 양이 많아,
 * [AuditReadInterceptor] 가 화면 진입 한 번당 한 행만 남긴다.
 */
enum class AuditType {
    LOGIN, LOGOUT, CREATE, UPDATE, DELETE, STATUS,
    MENU_VIEW, DATA_VIEW,
}
