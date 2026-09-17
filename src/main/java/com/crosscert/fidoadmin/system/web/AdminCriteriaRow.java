package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.system.entity.CcfaCriteria;
import java.time.LocalDateTime;

/** 목록용 DTO. CLOB(JSONDATA) 제외. */
public record AdminCriteriaRow(Long idx, String aaid, String metahash, LocalDateTime updatedtime) {
    public static AdminCriteriaRow of(CcfaCriteria c) {
        return new AdminCriteriaRow(c.getIdx(), c.getAaid(), c.getMetahash(), c.getUpdatedtime());
    }
}
