package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ErrorCodeSearchForm extends SearchForm {
    private String errorCode;
    private String errorType;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("errorCode", errorCode); m.put("errorType", errorType);
        return m;
    }
}
