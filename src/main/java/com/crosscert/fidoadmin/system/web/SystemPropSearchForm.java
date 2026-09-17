package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** 시스템 설정 검색: 키(부분 일치), 고객사(기반 companyIdx). */
@Getter @Setter
public class SystemPropSearchForm extends SearchForm {
    private String propKey;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("propKey", propKey);
        return m;
    }
}
