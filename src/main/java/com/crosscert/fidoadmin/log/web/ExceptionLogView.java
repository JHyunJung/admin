package com.crosscert.fidoadmin.log.web;

import com.crosscert.fidoadmin.log.entity.CcfaExceptions;

/** 상세. ERD 8컬럼 전부. 속성명은 뷰에서 다루기 쉽게 정리한다(eType → type 등). */
public record ExceptionLogView(Long idx, Long companyIdx, String type, String level, String message,
                               String detailMessage, String data, String createdtime) {
    public static ExceptionLogView from(CcfaExceptions e) {
        return new ExceptionLogView(e.getIdx(), e.getCompanyIdx(), e.getEType(), e.getELevel(),
            e.getExceptionMessage(), e.getExceptionDetailMessage(), e.getExceptionData(), e.getCreatedtime());
    }
}
