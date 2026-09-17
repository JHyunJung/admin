package com.crosscert.fidoadmin.fido2.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class Fido2CredentialParamsSearchForm extends SearchForm {
    private String credType;
    private String status;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("credType", credType); m.put("status", status);
        return m;
    }
}
