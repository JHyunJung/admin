package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.fido.entity.Sign;
import java.time.LocalDateTime;

/** SIGN 목록 행. CLOB(PLAINTEXT)·DATA·SIGNATURE 는 싣지 않고 ASSERTION 은 앞 32자만 보여준다. */
public record SignRow(Long idx, Long companyIdx, String userid, String dn, String memo, String del,
                      LocalDateTime createtime, String assertionHead) {

    static final int HEAD = 32;

    public static SignRow of(Sign s) {
        String a = s.getAssertion();
        String head = a == null ? null : a.length() <= HEAD ? a : a.substring(0, HEAD) + "…";
        return new SignRow(s.getIdx(), s.getCompanyIdx(), s.getUserid(), s.getDn(), s.getMemo(), s.getDel(),
            s.getCreatetime(), head);
    }
}
