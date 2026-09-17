package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** CRITERIA 검색 조건. COMPANY_IDX 가 없는 테이블이라 기반의 companyIdx 는 쓰이지 않는다. */
@Getter @Setter
public class CriteriaSearchForm extends SearchForm {
    private String aaid;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("aaid", aaid);
        return m;
    }
}
