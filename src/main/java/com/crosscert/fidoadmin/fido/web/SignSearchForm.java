package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** SIGN 검색 조건. 기간은 기반의 fromDate/toDate(CREATETIME 기준). del 은 'Y'/'N'/빈값(전체). */
@Getter @Setter
public class SignSearchForm extends SearchForm {
    private String userid;
    private String del;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userid", userid); m.put("del", del);
        return m;
    }
}
