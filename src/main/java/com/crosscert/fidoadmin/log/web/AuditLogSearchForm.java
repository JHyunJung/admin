package com.crosscert.fidoadmin.log.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class AuditLogSearchForm extends SearchForm {
    private String userId;
    private String type;
    private String message;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", userId); m.put("type", type); m.put("message", message);
        return m;
    }
}
