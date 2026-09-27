package com.crosscert.fidoadmin.log.web;

import com.crosscert.fidoadmin.log.service.FidoLogJson;
import java.time.LocalDateTime;

/**
 * FIDO 로그 목록 행. CLOB(JSONDATA) 은 싣지 않는다 — 자리 자체가 없어야 템플릿을 고쳐도 새지 않는다.
 * 구분·사용자·결과는 DB 에서 {@code JSON_VALUE} 로 꺼낸 값이다({@link FidoLogJson}).
 * 날짜별 분할 테이블에서 JDBC 로 읽으므로 엔티티 변환 팩토리는 없다.
 *
 * @param op            등록/인증/해지 원문(Reg/Auth/Dereg). JSON 에 없으면 null
 * @param userid        사용자 ID. 없으면 null
 * @param result        결과 코드. 없으면 null
 * @param resultMessage CCFA_ERROR_TABLE 의 메시지. 코드가 사전에 없으면 null
 */
public record FidoLogRow(Long idx, Long companyIdx, String serialcode, String servicename, LocalDateTime createdtime,
                         String op, String userid, String result, String resultMessage) {

    public String opLabel() { return FidoLogJson.opLabel(op); }

    public boolean success() { return FidoLogJson.isSuccess(result); }
}
