package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.fido.entity.Transactionhash;
import java.time.LocalDateTime;

/** TRANSACTIONHASH 목록 행. CLOB(CONTENT) 은 싣지 않는다. */
public record TransactionhashRow(Long idx, Long companyIdx, String userid, String contenthash, LocalDateTime createtime) {

    public static TransactionhashRow of(Transactionhash t) {
        return new TransactionhashRow(t.getIdx(), t.getCompanyIdx(), t.getUserid(), t.getContenthash(), t.getCreatetime());
    }
}
