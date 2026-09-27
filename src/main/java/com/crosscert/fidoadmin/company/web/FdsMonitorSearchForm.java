package com.crosscert.fidoadmin.company.web;

import com.crosscert.fidoadmin.common.SearchForm;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * FDS 모니터링 검색 조건.
 *
 * <p>날짜 하나를 고른다 — FIDO_LOGS 는 날짜별로 테이블이 갈려 어느 테이블을 읽을지가 곧
 * 검색 조건이다({@code FidoLogSearchForm} 과 같은 이유). {@code term} 이 비어 있으면
 * 정책값을 쓴다. 하루치 테이블이므로 86400초를 넘는 기준은 의미가 없다.
 */
@Getter @Setter
public class FdsMonitorSearchForm extends SearchForm {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate logDate = LocalDate.now();

    private String servicename;

    @Min(value = 1, message = "반복 주기는 1초 이상이어야 합니다.")
    @Max(value = 86400, message = "반복 주기는 하루(86400초)를 넘을 수 없습니다.")
    private Integer term;

    @Override protected Map<String, Object> extraParams() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("logDate", logDate);
        m.put("servicename", servicename);
        m.put("term", term);
        return m;
    }
}
