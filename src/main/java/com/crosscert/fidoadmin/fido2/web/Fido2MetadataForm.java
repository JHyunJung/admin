package com.crosscert.fidoadmin.fido2.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.fido2.entity.Fido2Metadata;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** FIDO2_METADATA 입력 폼. 길이는 ERD 값(바이트). CLOB 두 개는 길이 제한 없음. */
@Getter @Setter
public class Fido2MetadataForm {
    @NotBlank @ByteSize(max = 256) private String description;
    @NotBlank @ByteSize(max = 256) private String aaguid;
    @ByteSize(max = 2048) private String alternativedescriptions;
    @ByteSize(max = 128) private String protocolfamily;
    private Long authenticatorversion;
    @ByteSize(max = 512) private String upv;
    @ByteSize(max = 128) private String assertionschme;
    private Long authenticationalgorithm;
    private Long publickeyalgandencoding;
    @ByteSize(max = 2048) private String attestationtypes;
    @ByteSize(max = 2048) private String userverificationdetails;
    private Long keyprotection;
    private Long matcherprotection;
    private Long cryptostrength;
    @ByteSize(max = 2048) private String operatingenv;
    private Long attachmenthint;
    @ByteSize(max = 8) private String issecondfactoronly = "false";
    private Long tcdisplay;
    private String attestationrootcertificates;
    private String icon;
    @ByteSize(max = 2048) private String ski;

    public static Fido2MetadataForm from(Fido2Metadata m) {
        Fido2MetadataForm f = new Fido2MetadataForm();
        f.description = m.getDescription(); f.aaguid = m.getAaguid();
        f.alternativedescriptions = m.getAlternativedescriptions(); f.protocolfamily = m.getProtocolfamily();
        f.authenticatorversion = m.getAuthenticatorversion(); f.upv = m.getUpv(); f.assertionschme = m.getAssertionschme();
        f.authenticationalgorithm = m.getAuthenticationalgorithm(); f.publickeyalgandencoding = m.getPublickeyalgandencoding();
        f.attestationtypes = m.getAttestationtypes(); f.userverificationdetails = m.getUserverificationdetails();
        f.keyprotection = m.getKeyprotection(); f.matcherprotection = m.getMatcherprotection();
        f.cryptostrength = m.getCryptostrength(); f.operatingenv = m.getOperatingenv();
        f.attachmenthint = m.getAttachmenthint(); f.issecondfactoronly = m.getIssecondfactoronly();
        f.tcdisplay = m.getTcdisplay(); f.attestationrootcertificates = m.getAttestationrootcertificates();
        f.icon = m.getIcon(); f.ski = m.getSki();
        return f;
    }

    public void applyTo(Fido2Metadata m) {
        m.setDescription(description); m.setAaguid(aaguid);
        m.setAlternativedescriptions(alternativedescriptions); m.setProtocolfamily(protocolfamily);
        m.setAuthenticatorversion(authenticatorversion); m.setUpv(upv); m.setAssertionschme(assertionschme);
        m.setAuthenticationalgorithm(authenticationalgorithm); m.setPublickeyalgandencoding(publickeyalgandencoding);
        m.setAttestationtypes(attestationtypes); m.setUserverificationdetails(userverificationdetails);
        m.setKeyprotection(keyprotection); m.setMatcherprotection(matcherprotection);
        m.setCryptostrength(cryptostrength); m.setOperatingenv(operatingenv);
        m.setAttachmenthint(attachmenthint); m.setIssecondfactoronly(issecondfactoronly);
        m.setTcdisplay(tcdisplay); m.setAttestationrootcertificates(attestationrootcertificates);
        m.setIcon(icon); m.setSki(ski);
    }
}
