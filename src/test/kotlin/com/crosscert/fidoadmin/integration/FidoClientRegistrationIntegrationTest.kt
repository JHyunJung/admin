package com.crosscert.fidoadmin.integration

import com.crosscert.fidoadmin.system.entity.CcfaFidoclient
import com.crosscert.fidoadmin.system.repository.CcfaFidoclientRepository
import java.net.URI
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * FIDO 서버 자가등록 경로를 실제 Oracle + 전체 필터 체인으로 확인한다.
 *
 * MockMvc 를 전체 스프링 컨텍스트로 띄우는 이유는 이 기능의 위험이 서비스 로직이 아니라
 * *경계*에 있기 때문이다 — 로그인 리다이렉트, CSRF 403, 테넌트 선택 인터셉터 중
 * 하나라도 걸리면 FIDO 서버의 등록이 조용히 실패한다. 인증 없는 호출을 그대로 재현한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FidoClientRegistrationIntegrationTest : OracleContainerSupport() {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var repository: CcfaFidoclientRepository

    private fun find(serverName: String): CcfaFidoclient? =
        repository.findFirstByServernameOrderByCreatedtimeAsc(serverName).orElse(null)

    @Test
    fun 인증_없이_새_서버가_등록된다() {
        mvc.perform(post("/api/svc/reg/IT_FIDO_NEW").param("fsurl", "https://fido-new.internal:8443"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("\$.serverCode").value("IT_FIDO_NEW"))
            .andExpect(jsonPath("\$.status").value("ON"))

        val saved = find("IT_FIDO_NEW")
        assertThat(saved).isNotNull()
        assertThat(saved!!.serverurl).isEqualTo("https://fido-new.internal:8443")
        assertThat(saved.status).isEqualTo("ON")
        assertThat(saved.createdtime).isNotNull()
    }

    @Test
    fun 같은_이름으로_다시_등록하면_행이_쌓이지_않고_갱신된다() {
        mvc.perform(post("/api/svc/reg/IT_FIDO_REREG").param("fsurl", "https://old.internal:8443"))
            .andExpect(status().isOk())
        mvc.perform(post("/api/svc/reg/IT_FIDO_REREG").param("fsurl", "https://new.internal:9443"))
            .andExpect(status().isOk())

        assertThat(repository.findAll().stream()
            .filter { c -> "IT_FIDO_REREG" == c.servername }
            .count()).isEqualTo(1)
        assertThat(find("IT_FIDO_REREG")!!.serverurl).isEqualTo("https://new.internal:9443")
    }

    @Test
    fun localhost_는_실제_접속_주소로_바뀐다() {
        mvc.perform(post("/api/svc/reg/IT_FIDO_LOCAL")
                .param("fsurl", "https://localhost:8443/fido")
                .with { r -> r.remoteAddr = "10.1.2.3"; r })
            .andExpect(status().isOk())
            .andExpect(jsonPath("\$.serverUrl").value("https://10.1.2.3:8443/fido"))

        assertThat(find("IT_FIDO_LOCAL")!!.serverurl).isEqualTo("https://10.1.2.3:8443/fido")
    }

    @Test
    fun IPv6_로_접속하면_주소를_대괄호로_감싼다() {
        // 같은 장비의 FIDO 서버는 ::1 로 잡힌다. 대괄호가 없으면 포트와 구분되지 않아
        // https://0:0:0:0:0:0:0:1:8443 같은 접속 불가능한 URL 이 저장된다(실제로 겪은 결함).
        mvc.perform(post("/api/svc/reg/IT_FIDO_V6")
                .param("fsurl", "https://localhost:8443/fido")
                .with { r -> r.remoteAddr = "0:0:0:0:0:0:0:1"; r })
            .andExpect(status().isOk())
            .andExpect(jsonPath("\$.serverUrl").value("https://[0:0:0:0:0:0:0:1]:8443/fido"))

        assertThat(URI.create(find("IT_FIDO_V6")!!.serverurl).port).isEqualTo(8443)
    }

    @Test
    fun fsurl_이_없으면_아무것도_저장하지_않는다() {
        mvc.perform(post("/api/svc/reg/IT_FIDO_NOURL"))
            .andExpect(status().isBadRequest())

        assertThat(find("IT_FIDO_NOURL")).isNull()
    }

    @Test
    fun 등록_해제는_행을_지우지_않고_OFF_로_내린다() {
        mvc.perform(post("/api/svc/reg/IT_FIDO_DEREG").param("fsurl", "https://dereg.internal:8443"))
            .andExpect(status().isOk())

        mvc.perform(post("/api/svc/deReg/IT_FIDO_DEREG"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("\$.status").value("OFF"))

        val saved = find("IT_FIDO_DEREG")
        assertThat(saved).isNotNull() // 지우지 않는다
        assertThat(saved!!.status).isEqualTo("OFF")
        assertThat(saved.serverurl).isEqualTo("https://dereg.internal:8443") // URL 은 남는다
    }

    @Test
    fun 등록된_적_없는_서버의_해제는_404_다() {
        mvc.perform(post("/api/svc/deReg/IT_FIDO_UNKNOWN"))
            .andExpect(status().isNotFound())
    }

    @Test
    fun 다른_화면은_여전히_막혀_있다() {
        // /api/svc/** 를 여는 것이 나머지 경로까지 열어버리지 않았는지 확인한다.
        // POST 는 CSRF 가 먼저 막아 403 이고(토큰 없는 익명 호출), GET 은 로그인으로 보낸다.
        mvc.perform(post("/system/fido-clients"))
            .andExpect(status().isForbidden())
        mvc.perform(get("/system/fido-clients"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("**/login"))
    }
}
