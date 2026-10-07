package com.crosscert.fidoadmin.log.web;

import com.crosscert.fidoadmin.log.service.FidoLogPayload;
import java.time.LocalDateTime;

/**
 * FIDO 로그 목록 행. CLOB(JSONDATA) 은 싣지 않는다 — 자리 자체가 없어야 템플릿을 고쳐도 새지 않는다.
 * 구분·서비스명·사용자·인증장치는 JSONDATA 를 풀어 꺼낸 값이다({@link FidoLogPayload}).
 * 칸 구성은 이전 어드민 목록(번호 · 구분 · 사용자 · 서비스명 · FIDO종류 · 타입 · 사용자ID · 요청IP · 인증장치 ·
 * 로깅시간 · UserAgent · 상태)과 같다. 상태는 운영자가 목록만 보고 고객이 정상 진행했는지 확인하는 데 쓴다.
 *
 * @param op          구분 원문(Reg/Auth/DeReg/TC). 풀지 못하면 null
 * @param user        사용자 이름에서 {@code _*} 꼬리를 뗀 앞부분({@link FidoLogPayload#userHead()}). 없으면 null
 * @param servicename JSON 의 서비스명. 없으면 SERVICENAME 컬럼 값
 * @param fidoKind    FIDO / KFIDO. 사용자 이름이 없으면 null
 * @param type        구분에 TC 판정을 더한 값({@link FidoLogPayload#type()})
 * @param userid      사용자 ID — 사용자 이름(userName) 원문. 이전 어드민처럼 {@code _*서비스} 꼬리까지 그대로다. 없으면 null
 * @param accessIp    요청 IP. 없으면 null
 * @param bioType     인증장치 코드. 없으면 null
 * @param logtime     FIDO 서버가 남긴 로깅시간 원문. 없으면 null(화면은 CREATEDTIME 을 쓴다)
 * @param userAgent   UserAgent. 없으면 null
 * @param status      처리 상태 원문(RequestOK/ResponseOK/Success/Error/Wait). 없으면 null
 */
public record FidoLogRow(Long idx, Long companyIdx, String op, String user, String servicename, String fidoKind,
                         String type, String userid, String accessIp, Long bioType, String logtime,
                         String userAgent, String status, LocalDateTime createdtime) {

    public static FidoLogRow of(Long idx, Long companyIdx, String columnServicename, LocalDateTime createdtime,
                                FidoLogPayload payload) {
        String servicename = payload.serviceName() != null ? payload.serviceName() : columnServicename;
        return new FidoLogRow(idx, companyIdx, payload.op(), payload.userHead(), servicename, payload.fidoKind(),
            payload.type(), payload.userName(), payload.accessIp(), payload.bioType(), payload.logtime(),
            payload.userAgent(), payload.status(), createdtime);
    }

    public String bioTypeLabel() { return FidoLogPayload.bioTypeLabel(bioType); }

    /** 이전 어드민 목록의 말줄임: 40자를 넘으면 앞 38자에 " ..." 를 붙인다. 전체는 title 로 보인다. */
    public static String shorten(String value) {
        if (value == null) return "-";
        return value.length() > 40 ? value.substring(0, 38) + " ..." : value;
    }
}
