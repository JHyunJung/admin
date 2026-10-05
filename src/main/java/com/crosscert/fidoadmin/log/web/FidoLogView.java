package com.crosscert.fidoadmin.log.web;

import com.crosscert.fidoadmin.common.JsonPretty;
import com.crosscert.fidoadmin.log.service.FidoLogPayload;
import java.time.LocalDateTime;

/**
 * 상세. JSONDATA 는 풀어서(base64url 디코드) 정리한 JSON 을 담는다. 풀지 못하면 원문 그대로.
 * 구분·서비스명·사용자·인증장치·상태는 목록과 같은 해석({@link FidoLogPayload})에서 꺼낸 값이다.
 */
public record FidoLogView(Long idx, Long companyIdx, String serialcode, String servicename,
                          LocalDateTime createdtime, String jsondataPretty,
                          String op, String userid, Long bioType, String status) {

    /** 날짜별 분할 테이블에서 읽은 값으로 만든다(엔티티가 없다 — 테이블 이름이 조회 시점에 정해진다). */
    public static FidoLogView of(Long idx, Long companyIdx, String serialcode, String columnServicename,
                                 LocalDateTime createdtime, String jsondata) {
        FidoLogPayload p = FidoLogPayload.parse(jsondata);
        String servicename = p.serviceName() != null ? p.serviceName() : columnServicename;
        String pretty = JsonPretty.pretty(p.json() != null ? p.json() : jsondata);
        return new FidoLogView(idx, companyIdx, serialcode, servicename, createdtime, pretty,
            p.op(), p.userName(), p.bioType(), p.status());
    }

    public String bioTypeLabel() { return FidoLogPayload.bioTypeLabel(bioType); }
}
