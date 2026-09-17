package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class MenuSearchForm extends SearchForm {
    private String menuName;
    private String menuCode;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("menuName", menuName); m.put("menuCode", menuCode);
        return m;
    }
}
