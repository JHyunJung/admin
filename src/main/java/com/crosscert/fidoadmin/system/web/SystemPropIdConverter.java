package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * "/system/props/{id}" 의 경로 변수를 복합키로 바꾼다.
 * Converter 빈은 Spring Boot 가 FormatterRegistry 에 자동 등록한다(@WebMvcTest 도 포함).
 */
@Component
public class SystemPropIdConverter implements Converter<String, CcfaSystemPropId> {

    @Override
    public CcfaSystemPropId convert(String source) {
        return CcfaSystemPropId.parse(source);
    }
}
