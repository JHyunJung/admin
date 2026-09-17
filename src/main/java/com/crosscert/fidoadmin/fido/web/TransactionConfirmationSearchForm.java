package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/** TRANSACTION_CONFIRMATION 검색 조건. 기간은 기반의 fromDate/toDate(CREATEDTIME 기준). */
@Getter @Setter
public class TransactionConfirmationSearchForm extends SearchForm {
    private String userid;
    private String aaid;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userid", userid); m.put("aaid", aaid);
        return m;
    }
}
