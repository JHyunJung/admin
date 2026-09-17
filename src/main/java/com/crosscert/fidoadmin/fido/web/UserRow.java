package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.fido.entity.Userinfo;
import java.time.LocalDateTime;

/** USERINFO 목록 행. PUBKEY/CERTIFICATE 는 싣지 않는다. */
public record UserRow(Long idx, Long companyIdx, String servicename, String userid, String aaid,
                      String status, Long signcounter, LocalDateTime regtime) {

    public static UserRow from(Userinfo u) {
        return new UserRow(u.getIdx(), u.getCompanyIdx(), u.getServicename(), u.getUserid(), u.getAaid(),
            u.getStatus(), u.getSigncounter(), u.getRegtime());
    }
}
