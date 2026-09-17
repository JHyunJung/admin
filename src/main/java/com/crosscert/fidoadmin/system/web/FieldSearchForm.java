package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class FieldSearchForm extends SearchForm {
    private String fieldTable;
    private String fieldName;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fieldTable", fieldTable); m.put("fieldName", fieldName);
        return m;
    }
}
