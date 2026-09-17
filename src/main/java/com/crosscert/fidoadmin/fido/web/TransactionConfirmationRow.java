package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.fido.entity.TransactionConfirmation;
import java.time.LocalDateTime;

/** TRANSACTION_CONFIRMATION 목록 행. CLOB(CONTENT) 은 싣지 않는다. */
public record TransactionConfirmationRow(Long idx, Long companyIdx, String userid, String aaid, String contenttype,
                                         LocalDateTime createdtime) {

    public static TransactionConfirmationRow of(TransactionConfirmation t) {
        return new TransactionConfirmationRow(t.getIdx(), t.getCompanyIdx(), t.getUserid(), t.getAaid(),
            t.getContenttype(), t.getCreatedtime());
    }
}
