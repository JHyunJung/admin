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
 *
 * <p>번호(IDX)는 컬럼이라 DB 가 거른다. 구분·서비스명·사용자·인증장치·상태는 base64url 로 인코딩된
 * JSONDATA 안의 값이라 DB 가 거를 수 없다.
 * 하나라도 있으면 서비스가 그날 로그를 풀어 Java 에서 거른다({@code FidoLogQueryService}).
 */
@Getter @Setter
public class FidoLogSearchForm extends SearchForm {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate logDate = LocalDate.now();

    /** 서비스명 부분 일치(대소문자 무시). */
    private String servicename;
    /** 구분(Reg/Auth/DeReg/TC). 그 밖의 값은 조건 없음. */
    private String op;
    /** 사용자 ID 부분 일치(대소문자 무시). */
    private String userid;
    /** 상태(Success/Error/Wait/RequestOK/ResponseOK) 일치(대소문자 무시). */
    private String status;
    /** 인증장치 코드(BioType) 일치. 비면 조건 없음. */
    private Long bioType;
    /** 번호(IDX) 일치. 비면 조건 없음. */
    private Long idx;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("logDate", logDate);
        m.put("servicename", servicename);
        m.put("op", op);
        m.put("userid", userid);
        m.put("status", status);
        m.put("bioType", bioType);
        m.put("idx", idx);
        return m;
    }
}
