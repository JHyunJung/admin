package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.fido.entity.Criteria;
import java.time.LocalDateTime;

/** AAID(정책) 목록 행. CLOB(JSONDATA) 은 싣지 않는다. */
public record CriteriaRow(
        Long idx,
        String aaid,
        String vendorids,
        Long userverification,
        Long keyprotection,
        Long matcherprotection,
        Long authenticatorversion,
        String metahash,
        LocalDateTime updatedtime,
        String assertionschemes,
        LocalDateTime createtime
) {
    public static CriteriaRow of(Criteria c) {
        return new CriteriaRow(
                c.getIdx(),
                c.getAaid(),
                c.getVendorids(),
                c.getUserverification(),
                c.getKeyprotection(),
                c.getMatcherprotection(),
                c.getAuthenticatorversion(),
                c.getMetahash(),
                c.getUpdatedtime(),
                c.getAssertionschemes(),
                c.getCreatetime()
        );
    }
}
