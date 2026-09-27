package com.crosscert.fidoadmin.log.web;

import com.crosscert.fidoadmin.common.JsonPretty;
import java.time.LocalDateTime;

/** 상세. JSONDATA 는 정리(들여쓰기)해 담는다. JSON 이 아니면 원문 그대로. */
public record FidoLogView(Long idx, Long companyIdx, String serialcode, String servicename,
                          LocalDateTime createdtime, String jsondataPretty) {

    /** 날짜별 분할 테이블에서 읽은 값으로 만든다(엔티티가 없다 — 테이블 이름이 조회 시점에 정해진다). */
    public static FidoLogView of(Long idx, Long companyIdx, String serialcode, String servicename,
                                 LocalDateTime createdtime, String jsondata) {
        return new FidoLogView(idx, companyIdx, serialcode, servicename,
            createdtime, JsonPretty.pretty(jsondata));
    }
}
