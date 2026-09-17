package com.crosscert.fidoadmin.company.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.company.entity.CcfaFdsPolicy;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** CCFA_FDS_POLICY 입력 폼. 길이 제한은 ERD 값(바이트). */
@Getter @Setter
public class FdsPolicyForm {
    /** 할당형 PK. SUPER 만 고른다. COMPANY 는 기반 create() 가 자기 값으로 덮어쓴다. */
    private Long companyIdx;
    @ByteSize(max = 4000) private String andIp;
    @ByteSize(max = 32) private String andTerm;
    @ByteSize(max = 16) private String andDevice;
    @NotBlank(message = "AND 국가는 필수입니다.") @ByteSize(max = 16) private String andCountry;
    @ByteSize(max = 4000) private String orIp;
    @ByteSize(max = 32) private String orTerm;
    @NotBlank(message = "OR 국가는 필수입니다.") @ByteSize(max = 16) private String orCountry;

    public static FdsPolicyForm from(CcfaFdsPolicy p) {
        FdsPolicyForm f = new FdsPolicyForm();
        f.companyIdx = p.getCompanyIdx();
        f.andIp = p.getAndIp(); f.andTerm = p.getAndTerm(); f.andDevice = p.getAndDevice(); f.andCountry = p.getAndCountry();
        f.orIp = p.getOrIp(); f.orTerm = p.getOrTerm(); f.orCountry = p.getOrCountry();
        return f;
    }

    /** 식별자(companyIdx)는 여기서 건드리지 않는다. 등록 시에만 컨트롤러 toEntity 가 채운다. */
    public void applyTo(CcfaFdsPolicy p) {
        p.setAndIp(andIp); p.setAndTerm(andTerm); p.setAndDevice(andDevice); p.setAndCountry(andCountry);
        p.setOrIp(orIp); p.setOrTerm(orTerm); p.setOrCountry(orCountry);
    }
}
