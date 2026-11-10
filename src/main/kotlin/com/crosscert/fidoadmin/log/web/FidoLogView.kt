package com.crosscert.fidoadmin.log.web

import com.crosscert.fidoadmin.common.JsonPretty
import com.crosscert.fidoadmin.log.service.FidoLogPayload
import java.time.LocalDateTime

/**
 * 상세. JSONDATA 는 풀어서(base64url 디코드) 정리한 JSON 을 담는다. 풀지 못하면 원문 그대로.
 * 구분·사용자·서비스명·FIDO종류·타입·요청IP·인증장치·로깅시간·UserAgent·상태는 목록과 같은
 * 해석([FidoLogPayload])에서 꺼낸 값이다.
 */
@JvmRecord
data class FidoLogView(
    val idx: Long?, val companyIdx: Long?, val serialcode: String?, val servicename: String?,
    val createdtime: LocalDateTime?, val jsondataPretty: String?,
    val op: String?, val user: String?, val fidoKind: String?, val type: String?, val userid: String?,
    val accessIp: String?, val bioType: Long?, val logtime: String?, val userAgent: String?, val status: String?,
) {

    companion object {
        /** 날짜별 분할 테이블에서 읽은 값으로 만든다(엔티티가 없다 — 테이블 이름이 조회 시점에 정해진다). */
        @JvmStatic
        fun of(idx: Long?, companyIdx: Long?, serialcode: String?, columnServicename: String?,
               createdtime: LocalDateTime?, jsondata: String?): FidoLogView {
            val p = FidoLogPayload.parse(jsondata)
            val servicename = p.serviceName ?: columnServicename
            val pretty = JsonPretty.pretty(p.json ?: jsondata)
            return FidoLogView(idx, companyIdx, serialcode, servicename, createdtime, pretty,
                p.op, p.userHead(), p.fidoKind(), p.type(), p.userName, p.accessIp, p.bioType, p.logtime,
                p.userAgent, p.status)
        }
    }
}
