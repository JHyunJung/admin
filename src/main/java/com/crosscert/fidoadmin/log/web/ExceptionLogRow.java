package com.crosscert.fidoadmin.log.web;

import com.crosscert.fidoadmin.log.entity.CcfaExceptions;

/** 목록 행. CLOB(EXCEPTION_DATA) 과 상세 메시지는 싣지 않는다. createdtime 은 원본대로 문자열. */
public record ExceptionLogRow(Long idx, Long companyIdx, String type, String level, String message, String createdtime) {
    public static ExceptionLogRow from(CcfaExceptions e) {
        return new ExceptionLogRow(e.getIdx(), e.getCompanyIdx(), e.getEType(), e.getELevel(),
            e.getExceptionMessage(), e.getCreatedtime());
    }
}
