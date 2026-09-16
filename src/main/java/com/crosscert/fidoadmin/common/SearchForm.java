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

    /** 정렬 속성명 허용 형태(자바 식별자, 점 경로 허용). 그 밖은 기본 정렬로 되돌린다. */
    private static final java.util.regex.Pattern SORTABLE =
        java.util.regex.Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}(\\.[A-Za-z_][A-Za-z0-9_]{0,63}){0,3}");

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
            String property = parts[0].trim();
            // 사용자 입력이므로 속성명 형태를 제한한다. 매핑되지 않은 이름은 조회 시
            // 예외가 되어 500 이 되므로, 형식이 어긋나면 기본 정렬로 되돌린다.
            if (SORTABLE.matcher(property).matches()) {
                Sort.Direction dir = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())
                    ? Sort.Direction.ASC : Sort.Direction.DESC;
                sortObj = Sort.by(dir, property);
            }
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
