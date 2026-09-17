package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class AppserverSearchForm extends SearchForm {
    private String memberCode;
    private String memberId;
    private String type;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("memberCode", memberCode); m.put("memberId", memberId); m.put("type", type);
        return m;
    }
}
