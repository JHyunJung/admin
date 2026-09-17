package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.system.entity.CcfaSystemProp;
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * CCFA_SYSTEM_PROP 입력 폼. 식별자(propKey, companyIdx)는 등록 시에만 쓰이고 수정에서는 바뀌지 않는다.
 * PROP_KEY 는 CRUD 경로의 {id} 세그먼트 조합("{PROP_KEY}@{COMPANY_IDX}")으로 그대로 쓰이므로
 * URL·리다이렉트에 안전한 문자만 허용한다.
 */
@Getter @Setter
public class SystemPropForm {
    @NotBlank @ByteSize(max = 128)
    @Pattern(regexp = "[A-Za-z0-9._-]+", message = "키는 영문, 숫자, 마침표(.), 밑줄(_), 하이픈(-) 만 쓸 수 있습니다.")
    private String propKey;
    @NotNull private Long companyIdx = 0L;
    @ByteSize(max = 4000) private String propValue;
    @NotBlank @ByteSize(max = 20) private String shareType = "NO";

    public static SystemPropForm from(CcfaSystemProp p) {
        SystemPropForm f = new SystemPropForm();
        f.propKey = p.getId().getPropKey(); f.companyIdx = p.getId().getCompanyIdx();
        f.propValue = p.getPropValue(); f.shareType = p.getShareType();
        return f;
    }

    /** 신규 엔티티. 식별자는 여기서만 채운다. */
    public CcfaSystemProp toNewEntity() {
        CcfaSystemProp p = new CcfaSystemProp();
        p.setId(new CcfaSystemPropId(propKey == null ? null : propKey.trim(), companyIdx));
        applyTo(p);
        return p;
    }

    /** 수정 가능한 값만 반영한다. 식별자는 건드리지 않는다. */
    public void applyTo(CcfaSystemProp p) {
        p.setPropValue(propValue);
        p.setShareType(shareType);
    }
}
