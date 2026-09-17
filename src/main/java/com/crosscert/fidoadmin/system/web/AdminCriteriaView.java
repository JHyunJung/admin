package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.JsonPretty;
import com.crosscert.fidoadmin.system.entity.CcfaCriteria;
import java.time.LocalDateTime;

/** 상세용 DTO. JSONDATA 는 정리해서 보여준다. */
public record AdminCriteriaView(Long idx, String aaid, String metahash, String jsondataPretty,
                                LocalDateTime createtime, LocalDateTime updatedtime) {
    public static AdminCriteriaView of(CcfaCriteria c) {
        return new AdminCriteriaView(c.getIdx(), c.getAaid(), c.getMetahash(), JsonPretty.pretty(c.getJsondata()),
            c.getCreatetime(), c.getUpdatedtime());
    }
}
