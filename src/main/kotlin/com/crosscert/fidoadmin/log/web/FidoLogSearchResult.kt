package com.crosscert.fidoadmin.log.web

import org.springframework.data.domain.Page

/**
 * FIDO 로그 목록 결과.
 *
 * @param truncated 조건 검색이 하루치를 다 보지 못하고 상한(`FidoLogQueryService.SCAN_LIMIT`)에서 멈췄는가
 */
@JvmRecord
data class FidoLogSearchResult(val page: Page<FidoLogRow>, val truncated: Boolean)
