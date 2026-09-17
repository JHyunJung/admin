package com.crosscert.fidoadmin.log.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * CCFA_EXCEPTIONS 검색. CREATEDTIME 이 VARCHAR2(64) 라 base 의 fromDate/toDate 대신
 * 문자열 부분 일치(createdtime) 로 검색한다. 필드명은 엔티티 속성명(eType, eLevel) 과 같게 둔다.
 */
@Getter @Setter
public class ExceptionLogSearchForm extends SearchForm {
    private String eType;
    private String eLevel;
    private String message;
    private String createdtime;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("eType", eType); m.put("eLevel", eLevel); m.put("message", message); m.put("createdtime", createdtime);
        return m;
    }
}
