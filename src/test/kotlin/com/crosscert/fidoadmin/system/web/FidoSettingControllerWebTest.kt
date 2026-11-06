package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.auth.ManagerUserDetails
import com.crosscert.fidoadmin.common.GlobalExceptionHandler
import com.crosscert.fidoadmin.common.MenuRegistry
import com.crosscert.fidoadmin.common.SelectedTenant
import com.crosscert.fidoadmin.common.TenantContext
import com.crosscert.fidoadmin.company.service.CompanyLookup
import com.crosscert.fidoadmin.config.CurrentPathAdvice
import com.crosscert.fidoadmin.config.SecurityConfig
import com.crosscert.fidoadmin.config.WebMvcConfig
import com.crosscert.fidoadmin.system.service.FidoSettingService
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

@WebMvcTest(controllers = [FidoSettingController::class])
@Import(SecurityConfig::class, WebMvcConfig::class, CurrentPathAdvice::class, MenuRegistry::class,
         GlobalExceptionHandler::class, TenantContext::class, SelectedTenant::class)
class FidoSettingControllerWebTest {

    @Autowired lateinit var mvc: MockMvc
    @Autowired lateinit var selected: SelectedTenant
    @MockitoBean lateinit var service: FidoSettingService
    @MockitoBean lateinit var companies: CompanyLookup
    @MockitoBean lateinit var success: com.crosscert.fidoadmin.auth.LoginSuccessHandler
    @MockitoBean lateinit var failure: com.crosscert.fidoadmin.auth.LoginFailureHandler
    @MockitoBean lateinit var logout: com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler
    @MockitoBean lateinit var uds: com.crosscert.fidoadmin.auth.ManagerUserDetailsService

    val superUser =
        ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true)
    val companyUser =
        ManagerUserDetails(2L, "kbadmin", null, "국민", 5L, "KB", true, true)

    lateinit var session: MockHttpSession

    @BeforeEach fun setUp() {
        whenever(service.load()).thenReturn(mapOf(
            "CHALLENGE_TERM" to "180", "SMTP_PORT" to "25",
            "CERT_P1" to "ENABLE", "CERT_VERIFY" to "no",
            "FIDO_ATTESTCERT_AAID_CHECK" to "Y"))
        session = selecting(9L)
    }

    /** 세션 스코프 빈에 선택값을 넣는다. 이 슬라이스에는 TenantSelectionController 가 없다. */
    private fun selecting(companyIdx: Long): MockHttpSession {
        val s = MockHttpSession()
        val request = MockHttpServletRequest()
        request.setSession(s)
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
        try {
            selected.select(companyIdx)
        } finally {
            RequestContextHolder.resetRequestAttributes()
        }
        return s
    }

    @Test fun 화면이_열린다() {
        mvc.perform(get("/system/settings").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("system/settings/form"))
            .andExpect(content().string(Matchers.containsString("FIDO 서버 설정")))
    }

    /**
     * 토글의 hidden/checkbox 값이 그 키의 표기여야 한다. 하드코딩된 Y/N 으로 되돌아가면
     * 화면은 멀쩡한데 FIDO 서버가 못 읽는 값이 저장된다.
     */
    @Test fun 토글은_키별_표기로_렌더된다() {
        val html = mvc.perform(get("/system/settings").session(session).with(user(superUser)))
            .andReturn().response.contentAsString

        assertThat(html).contains("name=\"CERT_P1\" value=\"DISABLE\"")
        assertThat(html).contains("name=\"CERT_P1\" value=\"ENABLE\"")
        assertThat(html).contains("name=\"FIDO_ATTESTCERT_AAID_CHECK\" value=\"N\"")
        // CERT_VERIFY 는 드롭다운이다. 저장값(no)이 선택된 채로 그려진다.
        assertThat(html).containsPattern("<option value=\"no\"\\s+selected=\"selected\">검증안함</option>")
        assertThat(html).contains("<option value=\"yes\">검증함</option>")
    }

    /** 섹션 순서: 알림메일 → 부가기능 → 인증서. 위젯은 슬라이더·드롭다운. */
    @Test fun 섹션_순서와_위젯_규격() {
        val html = mvc.perform(get("/system/settings").session(session).with(user(superUser)))
            .andReturn().response.contentAsString
        assertThat(html.indexOf(">알림메일 설정<")).isLessThan(html.indexOf(">FIDO 부가기능 설정<"))
        assertThat(html.indexOf(">FIDO 부가기능 설정<")).isLessThan(html.indexOf(">인증서 설정<"))
        assertThat(html).contains("type=\"range\"").contains("180 sec")
            .contains(">영구저장<").contains(">사용안함<").contains(">검증함<")
            .contains("인증 응답시 추가 옵션").contains("PKCS #7 + PKCS #9")
    }

    @Test fun 세_섹션이_모두_그려진다() {
        mvc.perform(get("/system/settings").session(session).with(user(superUser)))
            .andExpect(content().string(Matchers.containsString("인증서 설정")))
            .andExpect(content().string(Matchers.containsString("FIDO 부가기능 설정")))
            .andExpect(content().string(Matchers.containsString("알림메일 설정")))
    }

    /** SUPER 전용 화면이다. COMPANY 계정은 URL 로도 못 연다. */
    @Test fun COMPANY_계정은_접근할_수_없다() {
        mvc.perform(get("/system/settings").with(user(companyUser)))
            .andExpect(status().isForbidden())
    }

    @Test fun 저장하면_서비스로_전달되고_되돌아온다() {
        mvc.perform(post("/system/settings").session(session).with(user(superUser)).with(csrf())
                .param("SMTP_IP", "10.0.0.1").param("CHALLENGE_TERM", "90"))
            .andExpect(status().is3xxRedirection())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .redirectedUrl("/system/settings"))

        val captor = argumentCaptor<Map<String, String>>()
        verify(service).save(captor.capture())
        assertThat(captor.lastValue).containsEntry("SMTP_IP", "10.0.0.1")
        assertThat(captor.lastValue).containsEntry("CHALLENGE_TERM", "90")
    }

    /**
     * 회귀 테스트. 토글은 hidden("끔")과 체크박스("켬")를 같은 이름으로 함께 보내는데,
     * 컨트롤러의 `@RequestParam Map<String,String>` 은 중복 파라미터에서
     * **첫 값만** 취한다. 따라서 체크박스가 hidden 보다 앞서야 한다 —
     * 순서가 뒤집히면 화면에서 아무리 켜도 항상 "끔"이 저장된다(실제로 그랬다).
     */
    @Test fun 토글은_체크박스가_hidden_보다_앞에_온다() {
        val html = mvc.perform(get("/system/settings").session(session).with(user(superUser)))
            .andReturn().response.contentAsString

        for (k in FidoSettingKey.values()) {
            if (k.type() != FidoSettingKey.Type.TOGGLE) continue
            if (k == FidoSettingKey.CERT_VERIFY) continue // 드롭다운이다(한 값만 전송)
            val checkbox = html.indexOf("name=\"" + k.key() + "\" value=\"" + k.style().on() + "\"")
            val hidden = html.indexOf("name=\"" + k.key() + "\" value=\"" + k.style().off() + "\"")
            assertThat(checkbox).`as`("%s 체크박스가 렌더되지 않았다", k.key()).isGreaterThanOrEqualTo(0)
            assertThat(hidden).`as`("%s hidden 이 렌더되지 않았다", k.key()).isGreaterThanOrEqualTo(0)
            assertThat(checkbox).`as`("%s: 체크박스가 hidden 보다 앞에 와야 한다", k.key()).isLessThan(hidden)
        }
    }

    /** 브라우저가 보내는 형태(체크박스 + hidden 중복)에서 "켬" 값이 서비스까지 도달해야 한다. */
    @Test fun 토글을_켜면_켠_값이_서비스로_간다() {
        mvc.perform(post("/system/settings").session(session).with(user(superUser)).with(csrf())
                .param("CERT_P1", "ENABLE", "DISABLE")
                .param("CERT_P7", "DISABLE"))
            .andExpect(status().is3xxRedirection())

        val captor = argumentCaptor<Map<String, String>>()
        verify(service).save(captor.capture())
        assertThat(captor.lastValue).containsEntry("CERT_P1", "ENABLE")
        assertThat(captor.lastValue).containsEntry("CERT_P7", "DISABLE")
    }

    /** 상태를 바꾸는 요청은 CSRF 토큰이 있어야 한다. */
    @Test fun CSRF_없는_저장은_거부된다() {
        mvc.perform(post("/system/settings").session(session).with(user(superUser))
                .param("SMTP_IP", "10.0.0.1"))
            .andExpect(status().isForbidden())
    }
}
