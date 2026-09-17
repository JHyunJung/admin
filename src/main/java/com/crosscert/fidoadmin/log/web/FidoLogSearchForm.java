package com.crosscert.fidoadmin.log.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** FIDO_LOGS 검색: 고객사(base companyIdx), 서비스명, 시리얼, 기간(base fromDate/toDate → CREATEDTIME). */
@Getter @Setter
public class FidoLogSearchForm extends SearchForm {
    private String servicename;
    private String serialcode;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("servicename", servicename); m.put("serialcode", serialcode);
        return m;
    }
}
