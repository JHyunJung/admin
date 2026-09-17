package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** CHALLENGE 검색 조건. 기간은 기반의 fromDate/toDate(CREATETIME 기준). */
@Getter @Setter
public class ChallengeSearchForm extends SearchForm {
    private String userid;
    private String servicename;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userid", userid); m.put("servicename", servicename);
        return m;
    }
}
