package com.crosscert.fidoadmin.system.reload

import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.audit.AuditType
import org.springframework.stereotype.Service

/** FIDO 서버 목록 화면의 수동 reload. 동기로 보내고 결과를 돌려주며 감사 로그를 남긴다. */
@Service
class FidoReloadService(
    private val client: FidoReloadClient,
    private val audit: AuditLogger,
) {

    fun reloadNow(): List<ReloadResult> {
        val results = client.reloadAll()
        val ok = results.count { it.ok }.toLong()
        audit.log(AuditType.UPDATE, "FIDO 서버 reload 수동 전송 | 성공 " + ok + " / 실패 " + (results.size - ok))
        return results
    }
}
