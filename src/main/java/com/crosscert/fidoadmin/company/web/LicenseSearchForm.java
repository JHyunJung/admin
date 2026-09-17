package com.crosscert.fidoadmin.company.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class LicenseSearchForm extends SearchForm {
    private String serviceName;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("serviceName", serviceName);
        return m;
    }
}
