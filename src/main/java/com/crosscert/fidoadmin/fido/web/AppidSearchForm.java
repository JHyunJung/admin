package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class AppidSearchForm extends SearchForm {
    private String appid;
    private String servicename;
    private String status;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("appid", appid); m.put("servicename", servicename); m.put("status", status);
        return m;
    }
}
