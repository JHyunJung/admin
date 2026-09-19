package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.system.SecretProps;
import com.crosscert.fidoadmin.system.entity.CcfaSystemProp;
import java.time.LocalDateTime;

/**
 * 목록·상세 출력 DTO. pathValue 가 링크에 쓰인다.
 *
 * <p>비밀값(SMTP_PASSWORD 등)은 값 대신 마스크를 싣는다. 이 화면은 임의 키를 직접 다루는
 * 저수준 화면이라, {@code /system/settings} 만 가리면 같은 행을 여기서 그대로 읽을 수 있다.
 */
public record SystemPropRow(String propKey, Long companyIdx, String propValue, String shareType,
                            LocalDateTime updatedtime, String pathValue) {
    public static SystemPropRow of(CcfaSystemProp p) {
        String key = p.getId().getPropKey();
        return new SystemPropRow(key, p.getId().getCompanyIdx(),
            SecretProps.forDisplay(key, p.getPropValue()),
            p.getShareType(), p.getUpdatedtime(), p.getId().toPathValue());
    }
}
