package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.fido.entity.Userinfo;
import java.time.LocalDateTime;

/** USERINFO 상세. PUBKEY/CERTIFICATE 는 앞 16자만(설계 2.2). 전체 값은 어떤 경로로도 뷰에 가지 않는다. */
public record UserView(Long idx, Long companyIdx, Long bioType, String servicename, String userid, String aaid,
                       Long authenticatorversion, String keyid, String pubkeyHead, String certificateHead,
                       Long signcounter, String uvs, String status, LocalDateTime regtime, LocalDateTime createdtime) {

    public static UserView from(Userinfo u) {
        return new UserView(u.getIdx(), u.getCompanyIdx(), u.getBioType(), u.getServicename(), u.getUserid(), u.getAaid(),
            u.getAuthenticatorversion(), u.getKeyid(), head16(u.getPubkey()), head16(u.getCertificate()),
            u.getSigncounter(), u.getUvs(), u.getStatus(), u.getRegtime(), u.getCreatedtime());
    }

    /** 앞 16자. null 은 null. */
    public static String head16(String s) {
        if (s == null) return null;
        return s.length() <= 16 ? s : s.substring(0, 16);
    }
}
