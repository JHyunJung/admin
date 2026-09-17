package com.crosscert.fidoadmin.log.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** CCFA_MAILING 검색: 상태, 수신자("TO" 컬럼 → 속성 to), SMS 상태. */
@Getter @Setter
public class MailingSearchForm extends SearchForm {
    private String status;
    private String to;
    private String smsStatus;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status); m.put("to", to); m.put("smsStatus", smsStatus);
        return m;
    }
}
