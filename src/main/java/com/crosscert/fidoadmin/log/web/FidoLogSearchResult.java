package com.crosscert.fidoadmin.log.web;

import org.springframework.data.domain.Page;

/**
 * FIDO 로그 목록 결과.
 *
 * @param truncated 조건 검색이 하루치를 다 보지 못하고 상한({@code FidoLogQueryService.SCAN_LIMIT})에서 멈췄는가
 */
public record FidoLogSearchResult(Page<FidoLogRow> page, boolean truncated) {}
