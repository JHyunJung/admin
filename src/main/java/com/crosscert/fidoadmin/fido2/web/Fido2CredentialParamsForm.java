package com.crosscert.fidoadmin.fido2.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.fido2.entity.Fido2CredentialParams;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** FIDO2_CREDENTIAL_PARAMS 입력 폼. CRED_ALG 는 COSE 알고리즘 번호(-7 ES256, -257 RS256, -8 EdDSA). */
@Getter @Setter
public class Fido2CredentialParamsForm {
    @NotBlank @ByteSize(max = 32) private String credType = "public-key";
    @NotNull private Long credAlg;
    @NotBlank @ByteSize(max = 1) private String status = "T";

    public static Fido2CredentialParamsForm from(Fido2CredentialParams p) {
        Fido2CredentialParamsForm f = new Fido2CredentialParamsForm();
        f.credType = p.getCredType(); f.credAlg = p.getCredAlg(); f.status = p.getStatus();
        return f;
    }

    public void applyTo(Fido2CredentialParams p) {
        p.setCredType(credType); p.setCredAlg(credAlg); p.setStatus(status);
    }
}
