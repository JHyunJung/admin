package com.crosscert.fidoadmin.log.web;

import com.crosscert.fidoadmin.common.JsonPretty;
import com.crosscert.fidoadmin.log.entity.FidoLogs;
import java.time.LocalDateTime;

/** 상세. JSONDATA 는 정리(들여쓰기)해 담는다. JSON 이 아니면 원문 그대로. */
public record FidoLogView(Long idx, Long companyIdx, String serialcode, String servicename,
                          LocalDateTime createdtime, String jsondataPretty) {
    public static FidoLogView from(FidoLogs e) {
        return new FidoLogView(e.getIdx(), e.getCompanyIdx(), e.getSerialcode(), e.getServicename(),
            e.getCreatedtime(), JsonPretty.pretty(e.getJsondata()));
    }
}
