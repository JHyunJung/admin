package com.crosscert.fidoadmin.fido2.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class Fido2MetadataSearchForm extends SearchForm {
    private String aaguid;
    private String description;
    private String protocolfamily;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("aaguid", aaguid); m.put("description", description); m.put("protocolfamily", protocolfamily);
        return m;
    }
}
