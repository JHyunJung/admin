package com.crosscert.fidoadmin.company.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CompanySearchForm extends SearchForm {
    private String companyName;
    private String companyType;
    private String enableType;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("companyName", companyName); m.put("companyType", companyType); m.put("enableType", enableType);
        return m;
    }
}
