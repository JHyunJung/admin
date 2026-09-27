package com.crosscert.fidoadmin.log.web;

import com.crosscert.fidoadmin.common.SearchForm;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * FIDO 로그 검색 조건.
 *
 * <p>FIDO_LOGS 는 날짜별로 테이블이 갈린다(FIDO_LOGS_YYYYMMDD). 그래서 기간(from~to)이
 * 아니라 <b>날짜 하나</b>를 고른다 — 어느 테이블을 읽을지가 곧 검색 조건이다.
 * 기본값은 오늘이다. 값이 없으면 읽을 테이블이 정해지지 않는다.
 */
@Getter @Setter
public class FidoLogSearchForm extends SearchForm {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate logDate = LocalDate.now();

    private String servicename;
    private String serialcode;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("logDate", logDate);
        m.put("servicename", servicename);
        m.put("serialcode", serialcode);
        return m;
    }
}
