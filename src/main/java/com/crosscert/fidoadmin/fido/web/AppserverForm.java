package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.fido.entity.Appserver;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** APPSERVER 입력 폼. MEMBER_CODE, MEMBER_ID, TYPE 은 ERD NOT NULL. */
@Getter @Setter
public class AppserverForm {
    @NotBlank @ByteSize(max = 32) private String memberCode;
    @NotBlank @ByteSize(max = 32) private String memberId;
    @NotBlank @ByteSize(max = 10) private String type = "use";
    @ByteSize(max = 128) private String note;
    /** SUPER 만 선택. COMPANY 는 서비스가 자기 고객사로 강제한다. */
    private Long companyIdx;

    public static AppserverForm from(Appserver a) {
        AppserverForm f = new AppserverForm();
        f.memberCode = a.getMemberCode(); f.memberId = a.getMemberId(); f.type = a.getType();
        f.note = a.getNote(); f.companyIdx = a.getCompanyIdx();
        return f;
    }

    public void applyTo(Appserver a) {
        a.setMemberCode(memberCode); a.setMemberId(memberId); a.setType(type);
        a.setNote(note); a.setCompanyIdx(companyIdx);
    }
}
