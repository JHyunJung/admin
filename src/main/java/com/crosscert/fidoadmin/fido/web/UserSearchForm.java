package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UserSearchForm extends SearchForm {
    private String userid;
    private String servicename;
    private String aaid;
    private String status;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userid", userid); m.put("servicename", servicename); m.put("aaid", aaid); m.put("status", status);
        return m;
    }
}
