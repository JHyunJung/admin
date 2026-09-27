package com.crosscert.fidoadmin.log.web;

import java.time.LocalDateTime;

/**
 * FIDO 로그 목록 행. CLOB(JSONDATA) 은 싣지 않는다 — 자리 자체가 없어야 템플릿을 고쳐도 새지 않는다.
 * 날짜별 분할 테이블에서 JDBC 로 읽으므로 엔티티 변환 팩토리는 없다.
 */
public record FidoLogRow(Long idx, Long companyIdx, String serialcode, String servicename, LocalDateTime createdtime) {
}
