package com.crosscert.fidoadmin.log.web;

import com.crosscert.fidoadmin.log.service.FidoLogPayload;
import java.time.LocalDateTime;

/**
 * FIDO 로그 목록 행. CLOB(JSONDATA) 은 싣지 않는다 — 자리 자체가 없어야 템플릿을 고쳐도 새지 않는다.
 * 구분·서비스명·사용자·인증장치는 JSONDATA 를 풀어 꺼낸 값이다({@link FidoLogPayload}).
 * 칸 구성은 이전 어드민 목록(구분 · 서비스명 · 사용자ID · 인증장치 · 로그시간)을 따른다.
 *
 * @param op          구분 원문(Reg/Auth/DeReg/TC). 풀지 못하면 null
 * @param servicename JSON 의 서비스명. 없으면 SERVICENAME 컬럼 값
 * @param userid      사용자 ID(userName). 없으면 null
 * @param bioType     인증장치 코드. 없으면 null
 */
public record FidoLogRow(Long idx, Long companyIdx, String op, String servicename, String userid, Long bioType,
                         LocalDateTime createdtime) {

    public static FidoLogRow of(Long idx, Long companyIdx, String columnServicename, LocalDateTime createdtime,
                                FidoLogPayload payload) {
        String servicename = payload.serviceName() != null ? payload.serviceName() : columnServicename;
        return new FidoLogRow(idx, companyIdx, payload.op(), servicename, payload.userName(), payload.bioType(), createdtime);
    }

    public String bioTypeLabel() { return FidoLogPayload.bioTypeLabel(bioType); }
}
