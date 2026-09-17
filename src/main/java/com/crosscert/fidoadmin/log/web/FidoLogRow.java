package com.crosscert.fidoadmin.log.web;

import com.crosscert.fidoadmin.log.entity.FidoLogs;
import java.time.LocalDateTime;

/** 목록 행. CLOB(JSONDATA) 은 싣지 않는다. */
public record FidoLogRow(Long idx, Long companyIdx, String serialcode, String servicename, LocalDateTime createdtime) {
    public static FidoLogRow from(FidoLogs e) {
        return new FidoLogRow(e.getIdx(), e.getCompanyIdx(), e.getSerialcode(), e.getServicename(), e.getCreatedtime());
    }
}
