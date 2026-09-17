package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.system.entity.CcfaSystemInfo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * CCFA_SYSTEM_INFO 입력 폼. propKey 는 등록 시에만 쓰인다.
 * PROP_KEY 는 CRUD 경로의 {id} 세그먼트로 그대로 쓰이므로 URL·리다이렉트에 안전한 문자만 허용한다.
 */
@Getter @Setter
public class SystemInfoForm {
    @NotBlank @ByteSize(max = 128)
    @Pattern(regexp = "[A-Za-z0-9._-]+", message = "키는 영문, 숫자, 마침표(.), 밑줄(_), 하이픈(-) 만 쓸 수 있습니다.")
    private String propKey;
    @ByteSize(max = 1024) private String propValue;

    public static SystemInfoForm from(CcfaSystemInfo i) {
        SystemInfoForm f = new SystemInfoForm();
        f.propKey = i.getPropKey(); f.propValue = i.getPropValue();
        return f;
    }

    public CcfaSystemInfo toNewEntity() {
        CcfaSystemInfo i = new CcfaSystemInfo();
        i.setPropKey(propKey == null ? null : propKey.trim());
        applyTo(i);
        return i;
    }

    /** 식별자는 바꾸지 않는다. */
    public void applyTo(CcfaSystemInfo i) { i.setPropValue(propValue); }
}
