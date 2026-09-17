package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.system.entity.CcfaFidoclient;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * CCFA_FIDOCLIENT 입력 폼. servercode 는 등록 시에만 쓰인다.
 * SERVERCODE 는 CRUD 경로의 {id} 세그먼트로 그대로 쓰이므로 URL·리다이렉트에 안전한 문자만 허용한다.
 */
@Getter @Setter
public class FidoClientForm {
    @NotBlank @ByteSize(max = 64)
    @Pattern(regexp = "[A-Za-z0-9._-]+", message = "코드는 영문, 숫자, 마침표(.), 밑줄(_), 하이픈(-) 만 쓸 수 있습니다.")
    private String servercode;
    @NotBlank @ByteSize(max = 1024) private String servername;
    @NotBlank @ByteSize(max = 2048) private String serverurl;
    @NotBlank @ByteSize(max = 4) private String status = "ON";

    public static FidoClientForm from(CcfaFidoclient c) {
        FidoClientForm f = new FidoClientForm();
        f.servercode = c.getServercode(); f.servername = c.getServername();
        f.serverurl = c.getServerurl(); f.status = c.getStatus();
        return f;
    }

    public CcfaFidoclient toNewEntity() {
        CcfaFidoclient c = new CcfaFidoclient();
        c.setServercode(servercode == null ? null : servercode.trim());
        applyTo(c);
        return c;
    }

    /** 식별자는 바꾸지 않는다. */
    public void applyTo(CcfaFidoclient c) {
        c.setServername(servername); c.setServerurl(serverurl); c.setStatus(status);
    }
}
