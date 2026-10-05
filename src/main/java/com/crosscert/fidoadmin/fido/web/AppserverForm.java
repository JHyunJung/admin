package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.fido.entity.Appserver;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * APPSERVER 입력 폼. MEMBER_ID, TYPE 은 ERD NOT NULL.
 * MEMBER_CODE 는 서버가 만들고 바꾸지 않는다 — 수정 화면에 보여 주기만 하고 엔티티에 옮기지 않는다.
 */
@Getter @Setter
public class AppserverForm {
    /** 표시 전용. applyTo 가 무시한다. */
    private String memberCode;
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
        a.setMemberId(memberId); a.setType(type);
        a.setNote(note); a.setCompanyIdx(companyIdx);
    }
}
