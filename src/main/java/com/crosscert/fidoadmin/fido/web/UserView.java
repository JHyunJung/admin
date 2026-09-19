package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.fido.entity.Userinfo;
import java.time.LocalDateTime;

/**
 * USERINFO 상세. PUBKEY/CERTIFICATE 는 앞 16자만(설계 2.2). 전체 값은 어떤 경로로도 뷰에 가지 않는다.
 *
 * <p>KEYID 도 같이 자른다. 설계 2.2 가 이름을 댄 컬럼은 아니지만 크리덴셜 식별자(1024자)이고,
 * 바로 옆의 PUBKEY 를 자르면서 이것만 통째로 내보내는 것은 앞뒤가 맞지 않는다.
 */
public record UserView(Long idx, Long companyIdx, Long bioType, String servicename, String userid, String aaid,
                       Long authenticatorversion, String keyidHead, String pubkeyHead, String certificateHead,
                       Long signcounter, String uvs, String status, LocalDateTime regtime, LocalDateTime createdtime) {

    public static UserView from(Userinfo u) {
        return new UserView(u.getIdx(), u.getCompanyIdx(), u.getBioType(), u.getServicename(), u.getUserid(), u.getAaid(),
            u.getAuthenticatorversion(), head16(u.getKeyid()), head16(u.getPubkey()), head16(u.getCertificate()),
            u.getSigncounter(), u.getUvs(), u.getStatus(), u.getRegtime(), u.getCreatedtime());
    }

    /** 앞 16자. null 은 null. */
    public static String head16(String s) {
        if (s == null) return null;
        return s.length() <= 16 ? s : s.substring(0, 16);
    }
}
