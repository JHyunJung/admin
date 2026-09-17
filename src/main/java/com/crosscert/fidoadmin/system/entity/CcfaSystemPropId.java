package com.crosscert.fidoadmin.system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class CcfaSystemPropId implements Serializable {
    @Column(name = "PROP_KEY", length = 128) private String propKey;
    @Column(name = "COMPANY_IDX") private Long companyIdx;

    /** URL 경로 한 조각으로 쓰는 표현. "{PROP_KEY}@{COMPANY_IDX}". */
    public String toPathValue() {
        return propKey + "@" + companyIdx;
    }

    /**
     * 경로 값 → 복합키. 키에 '@' 가 들어갈 수 있으므로 마지막 '@' 기준으로 나눈다.
     * 형식이 틀리면 IllegalArgumentException (컨트롤러에서는 400 으로 끝난다).
     */
    public static CcfaSystemPropId parse(String value) {
        if (value == null) throw new IllegalArgumentException("시스템 설정 키가 없습니다");
        int at = value.lastIndexOf('@');
        if (at <= 0 || at == value.length() - 1) {
            throw new IllegalArgumentException("시스템 설정 키 형식이 아닙니다: " + value);
        }
        String key = value.substring(0, at);
        try {
            return new CcfaSystemPropId(key, Long.parseLong(value.substring(at + 1)));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("시스템 설정 키 형식이 아닙니다: " + value, e);
        }
    }

    /** CrudController 가 리다이렉트 경로를 "basePath/" + id 로 만들므로 경로 값과 같게 둔다. */
    @Override public String toString() { return toPathValue(); }
}
