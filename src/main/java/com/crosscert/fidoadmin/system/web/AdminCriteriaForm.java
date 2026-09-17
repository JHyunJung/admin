package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.system.entity.CcfaCriteria;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** CCFA_CRITERIA 입력 폼. JSONDATA 는 CLOB 이라 길이 제한이 없고 형식만 컨트롤러가 검증한다. */
@Getter @Setter
public class AdminCriteriaForm {
    @NotBlank @ByteSize(max = 64) private String aaid;
    @ByteSize(max = 512) private String metahash;
    private String jsondata;

    public static AdminCriteriaForm from(CcfaCriteria c) {
        AdminCriteriaForm f = new AdminCriteriaForm();
        f.aaid = c.getAaid(); f.metahash = c.getMetahash(); f.jsondata = c.getJsondata();
        return f;
    }

    /** 식별자(IDX)는 시퀀스가 채우므로 폼이 건드리지 않는다. */
    public CcfaCriteria toNewEntity() {
        CcfaCriteria c = new CcfaCriteria();
        applyTo(c);
        return c;
    }

    public void applyTo(CcfaCriteria c) {
        c.setAaid(aaid); c.setMetahash(metahash);
        c.setJsondata(jsondata == null || jsondata.isBlank() ? null : jsondata); // EMPTY_CLOB 과 null 을 같게 본다
    }
}
