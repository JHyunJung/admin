package com.crosscert.fidoadmin.common;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;

/** 목록 화면 공통 검색 조건. 하위 클래스는 필드를 추가하고 extraParams() 에 넣는다. */
@Getter @Setter
public class SearchForm {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 200;

    private int page = 0;
    private int size = DEFAULT_SIZE;
    /** "속성,asc|desc" */
    private String sort;
    private Long companyIdx;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate fromDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate toDate;

    public Pageable toPageable(Sort defaultSort) {
        int s = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        Sort sortObj = defaultSort;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",", 2);
            Sort.Direction dir = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.ASC : Sort.Direction.DESC;
            sortObj = Sort.by(dir, parts[0].trim());
        }
        return PageRequest.of(Math.max(page, 0), s, sortObj);
    }

    public LocalDateTime fromDateTime() { return fromDate == null ? null : fromDate.atStartOfDay(); }
    public LocalDateTime toDateTimeExclusive() { return toDate == null ? null : toDate.plusDays(1).atStartOfDay(); }

    /** page 를 제외한 "&k=v&k=v" 문자열. 페이지네이션 링크에 붙인다. */
    public String toQueryString() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("size", size);
        params.put("sort", sort);
        params.put("companyIdx", companyIdx);
        params.put("fromDate", fromDate);
        params.put("toDate", toDate);
        params.putAll(extraParams());
        StringBuilder sb = new StringBuilder();
        params.forEach((k, v) -> {
            if (v == null || v.toString().isBlank()) return;
            sb.append('&').append(k).append('=').append(URLEncoder.encode(v.toString(), StandardCharsets.UTF_8));
        });
        return sb.toString();
    }

    protected Map<String, Object> extraParams() { return Map.of(); }
}
