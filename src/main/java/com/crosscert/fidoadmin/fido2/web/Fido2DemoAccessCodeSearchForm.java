package com.crosscert.fidoadmin.fido2.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class Fido2DemoAccessCodeSearchForm extends SearchForm {
    private String accesscode;
    private String vendorname;
    private String status;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("accesscode", accesscode); m.put("vendorname", vendorname); m.put("status", status);
        return m;
    }
}
