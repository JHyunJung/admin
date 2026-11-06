package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.common.javaIsBlank
import com.crosscert.fidoadmin.system.entity.CcfaFidoclient
import com.crosscert.fidoadmin.system.service.FidoClientService
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * FIDO 서버 자가등록 수신부. FIDO 서버가 기동하면서 스스로 자기 URL 을 알려오면 CCFA_FIDOCLIENT 에 반영한다.
 *
 * FIDO 서버가 부르는 `/api/svc/reg|deReg/{serverName}` 경로와 파라미터(`fsurl`)를 받는다.
 * FIDO 서버 쪽 코드를 고치지 않고 `License.adminServerUrl` 만 이 어드민으로 바꾸면 동작한다.
 *
 * **인증이 없는 경로다.** FIDO 서버는 로그인 세션 없이 호출하므로 `SecurityConfig` 에서
 * /api/svc/\*\* 를 permitAll + CSRF 제외로 열어 두었고, 테넌트 선택 인터셉터도 지나간다.
 * 따라서 누구나 서버를 등록하거나 OFF 로 내릴 수 있다 — 내부망 전제이며, 추적을 위해 접근 IP 를 남긴다.
 */
@RestController
@RequestMapping("/api/svc")
class FidoClientRegistrationController(
    private val service: FidoClientService,
) {

    @RequestMapping("/reg/{serverName}")
    fun register(
        @PathVariable serverName: String,
        @RequestParam(name = "fsurl", required = false) fsurl: String?,
        request: HttpServletRequest,
    ): ResponseEntity<Map<String, String?>> {
        log.info("[ClientAccess] Registration : {}", serverName)
        log.info("[ClientAccess] FS URL : {}", fsurl)
        log.info("[ClientAccess] Access IP : {}", request.remoteAddr)

        // 둘 중 하나가 비면 조용히 무시한다(응답 본문 없이 반환).
        if (serverName.javaIsBlank() || fsurl == null || fsurl.javaIsBlank()) {
            return ResponseEntity.badRequest().build()
        }

        // FIDO 서버가 자기 URL 을 localhost 로 인식해 보내오는 경우가 있다.
        // 어드민 입장에서 그 주소는 자기 자신이 되어 버리므로, 실제로 접속해 온 주소로 바꾼다.
        val serverUrl = fsurl.replace("localhost", urlHost(request.remoteAddr)!!)

        val saved = service.register(serverName, serverUrl)
        log.info("[ClientAccess] Registered : {} -> {}", saved.servercode, saved.serverurl)
        return ResponseEntity.ok(body(saved))
    }

    @RequestMapping("/deReg/{serverName}")
    fun deregister(
        @PathVariable serverName: String,
        request: HttpServletRequest,
    ): ResponseEntity<Map<String, String?>> {
        log.info("[ClientAccess] Deregistration : {} (Access IP : {})", serverName, request.remoteAddr)

        return service.deregister(serverName)
            .map { saved -> ResponseEntity.ok(body(saved)) }
            .orElseGet { ResponseEntity.notFound().build() }
    }

    companion object {
        private val log = LoggerFactory.getLogger(FidoClientRegistrationController::class.java)

        /**
         * URL 에 넣을 수 있는 형태의 호스트 표기로 바꾼다.
         *
         * IPv6 주소는 URL 안에서 대괄호로 감싸야 한다(RFC 3986). 감싸지 않으면
         * `https://0:0:0:0:0:0:0:1:8443/fido` 처럼 포트 구분이 불가능한 문자열이 되어,
         * 등록은 성공해도 그 URL 로는 접속할 수 없다. FIDO 서버가 어드민과 같은 장비에 있으면
         * 접속 주소가 IPv6 루프백(::1)으로 잡히므로 실제로 일어나는 경우다.
         */
        private fun urlHost(remoteAddr: String?): String? {
            if (remoteAddr == null || !remoteAddr.contains(":")) return remoteAddr // IPv4 또는 호스트명
            return if (remoteAddr.startsWith("[")) remoteAddr else "[$remoteAddr]"
        }

        /** 엔티티를 그대로 내보내지 않는다(집 규칙). 등록 결과 확인에 필요한 값만 담는다. */
        private fun body(client: CcfaFidoclient): Map<String, String?> = linkedMapOf(
            "serverCode" to client.servercode,
            "serverName" to client.servername,
            "serverUrl" to client.serverurl,
            "status" to client.status,
        )
    }
}
