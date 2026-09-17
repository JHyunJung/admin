package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.JsonPretty;
import com.crosscert.fidoadmin.fido.entity.Criteria;
import java.time.LocalDateTime;

/** CRITERIA 상세. ERD 17개 컬럼을 모두 담되 JSONDATA 는 정리된 문자열(jsondataPretty)로 준다. */
public record CriteriaView(Long idx, String aaid, String vendorids, Long userverification, Long keyprotection,
                           Long matcherprotection, Long attachmenthnumber, Long tcdisplay, String tcdisplaycontenttype,
                           String authenticationalgorithms, String assertionschemes, String attestationtypes,
                           Long authenticatorversion, String metahash, String jsondataPretty,
                           LocalDateTime createtime, LocalDateTime updatedtime) {

    public static CriteriaView of(Criteria c) {
        return new CriteriaView(c.getIdx(), c.getAaid(), c.getVendorids(), c.getUserverification(), c.getKeyprotection(),
            c.getMatcherprotection(), c.getAttachmenthnumber(), c.getTcdisplay(), c.getTcdisplaycontenttype(),
            c.getAuthenticationalgorithms(), c.getAssertionschemes(), c.getAttestationtypes(),
            c.getAuthenticatorversion(), c.getMetahash(), JsonPretty.pretty(c.getJsondata()),
            c.getCreatetime(), c.getUpdatedtime());
    }
}
