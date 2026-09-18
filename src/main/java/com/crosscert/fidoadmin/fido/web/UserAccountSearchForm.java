package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * 사용자 단위 목록의 검색 조건. 기기 단위 화면({@link UserSearchForm})과 달리
 * AAID·상태가 없다 — 한 사람의 기기마다 값이 다를 수 있어 묶음 행의 조건으로 맞지 않는다.
 * 그 조건으로 찾으려면 기기 목록 화면을 쓴다.
 */
@Getter @Setter
public class UserAccountSearchForm extends SearchForm {
    private String userid;
    private String servicename;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userid", userid);
        m.put("servicename", servicename);
        return m;
    }
}
