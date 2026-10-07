package com.crosscert.fidoadmin.log.web;

import com.crosscert.fidoadmin.log.service.FidoLogPayload;
import java.time.LocalDateTime;

/**
 * FIDO 로그 목록 행. CLOB(JSONDATA) 은 싣지 않는다 — 자리 자체가 없어야 템플릿을 고쳐도 새지 않는다.
 * 구분·서비스명·사용자ID·인증장치·상태는 JSONDATA 를 풀어 꺼낸 값이다({@link FidoLogPayload}).
 * 칸 구성은 운영 중인 이전 어드민 목록(번호 · 구분 · 서비스명 · 사용자ID · 인증장치 · 로깅시간 · 상태)과 같다.
 *
 * @param op          구분 원문(Reg/Auth/DeReg/TC). 풀지 못하면 null
 * @param servicename JSON 의 서비스명. 없으면 SERVICENAME 컬럼 값
 * @param userid      사용자 ID — 사용자 이름(userName) 원문. 이전 어드민처럼 {@code _*서비스} 꼬리까지 그대로다. 없으면 null
 * @param bioType     인증장치 코드. 없으면 null
 * @param status      처리 상태 원문(RequestOK/ResponseOK/Success/Error/Wait). 없으면 null
 */
public record FidoLogRow(Long idx, Long companyIdx, String op, String servicename, String userid, Long bioType,
                         String status, LocalDateTime createdtime) {

    public static FidoLogRow of(Long idx, Long companyIdx, String columnServicename, LocalDateTime createdtime,
                                FidoLogPayload payload) {
        String servicename = payload.serviceName() != null ? payload.serviceName() : columnServicename;
        return new FidoLogRow(idx, companyIdx, payload.op(), servicename, payload.userName(), payload.bioType(),
            payload.status(), createdtime);
    }

    public String bioTypeLabel() { return FidoLogPayload.bioTypeLabel(bioType); }

    /** 이전 어드민 목록의 말줄임: 40자를 넘으면 앞 38자에 " ..." 를 붙인다. 전체는 title 로 보인다. */
    public static String shorten(String value) {
        if (value == null) return "-";
        return value.length() > 40 ? value.substring(0, 38) + " ..." : value;
    }
}
