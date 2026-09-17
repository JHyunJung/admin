package com.crosscert.fidoadmin.manager.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ManagerSearchForm extends SearchForm {
    private String userId;
    private String userNm;
    private String status;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", userId); m.put("userNm", userNm); m.put("status", status);
        return m;
    }
}
