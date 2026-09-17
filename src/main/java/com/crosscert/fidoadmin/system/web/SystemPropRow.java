package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.system.entity.CcfaSystemProp;
import java.time.LocalDateTime;

/** 목록·상세 출력 DTO. pathValue 가 링크에 쓰인다. */
public record SystemPropRow(String propKey, Long companyIdx, String propValue, String shareType,
                            LocalDateTime updatedtime, String pathValue) {
    public static SystemPropRow of(CcfaSystemProp p) {
        return new SystemPropRow(p.getId().getPropKey(), p.getId().getCompanyIdx(), p.getPropValue(),
            p.getShareType(), p.getUpdatedtime(), p.getId().toPathValue());
    }
}
