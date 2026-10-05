package com.crosscert.fidoadmin.log.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.log.service.FidoLogQueryService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@WebMvcTest(controllers = FidoLogController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class, GlobalExceptionHandler.class, com.crosscert.fidoadmin.common.TenantContext.class, com.crosscert.fidoadmin.common.SelectedTenant.class})
class FidoLogControllerWebTest {

    @Autowired MockMvc mvc;
    @Autowired com.crosscert.fidoadmin.common.SelectedTenant selected;
    @MockitoBean FidoLogQueryService service;
    @MockitoBean CompanyLookup companies;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginSuccessHandler success;
    @MockitoBean com.crosscert.fidoadmin.auth.LoginFailureHandler failure;
    @MockitoBean com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.auth.ManagerUserDetailsService uds;

    ManagerUserDetails superUser = new ManagerUserDetails(1L, "superuser", null, "슈퍼", 0L, "전역", true, true);
    ManagerUserDetails companyUser = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);

    private FidoLogRow row(long idx) {
        return row(idx, "Auth", "user001", 2L);
    }

    private FidoLogRow row(long idx, String op, String userid, Long bioType) {
        return new FidoLogRow(idx, 1L, op, "com.kbstar.kbbank", userid, bioType, LocalDateTime.of(2026, 10, 2, 14, 42, 20));
    }

    private static FidoLogSearchResult result(List<FidoLogRow> rows, boolean truncated) {
        return new FidoLogSearchResult(new PageImpl<>(rows), truncated);
    }

    private FidoLogView detailOf(long idx, String jsondata) {
        return FidoLogView.of(idx, 1L, "SN-0001", "SERVICE", LocalDateTime.of(2026, 9, 16, 10, 0), jsondata);
    }

    /**
     * TenantSelectionInterceptor(Task 6)가 미선택 SUPER 를 /select-tenant 로 돌려보낸다.
     * 이 클래스가 보는 경로는 TENANT 영역이므로, SUPER 요청에는 미리 테넌트를 선택해 둔다.
     */
    MockHttpSession session;

    @BeforeEach void selectTenant() {
        session = new MockHttpSession();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            selected.select(9L);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test void companyListHidesCompanyFilterAndClob() throws Exception {
        when(service.search(any(), any())).thenReturn(result(List.of(row(5L)), false));
        mvc.perform(get("/logs/fido").param("servicename", "kbstar").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("log/fido/list"))
            .andExpect(content().string(containsString("com.kbstar.kbbank")))
            .andExpect(content().string(not(containsString("name=\"companyIdx\""))));
        // CLOB 은 목록 DTO(FidoLogRow)에 자리 자체가 없다. 화면 문자열이 아니라
        // 구조로 막혀 있음을 확인한다 — 템플릿을 고쳐도 되살아나지 않는다.
        org.assertj.core.api.Assertions
            .assertThat(java.util.Arrays.stream(FidoLogRow.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
            .doesNotContain("jsondata", "jsondataPretty");
        ArgumentCaptor<FidoLogSearchForm> captor = ArgumentCaptor.forClass(FidoLogSearchForm.class);
        verify(service).search(captor.capture(), any());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getServicename()).isEqualTo("kbstar");
    }

    /**
     * Task 10: 테넌트는 세션이 정하므로 SUPER 도 목록 검색폼에서 고객사를 따로 고르지 않는다.
     * "name=\"companyIdx\"" 만으로는 상단 고객사 전환 드롭다운의 hidden 필드와 구별되지 않으므로
     * 검색폼 select 태그로 특정한다.
     */
    @Test void superListDoesNotShowCompanyFilter() throws Exception {
        when(service.search(any(), any())).thenReturn(result(List.of(), false));
        mvc.perform(get("/logs/fido").session(session).with(user(superUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("<select name=\"companyIdx\""))))
            .andExpect(content().string(containsString("데이터가 없습니다.")));
    }

    /**
     * 상세는 JSONDATA 를 정리해 보여준다. th:text 는 따옴표를 &quot; 로 이스케이프하므로
     * 정리된 형태 `"op" : "Auth"` 는 HTML 에서 `&quot;op&quot; : &quot;Auth&quot;` 로 나타난다.
     */
    /** 서비스가 404 로 던진 ResponseStatusException 은 500 이 아니라 404 화면이어야 한다(없는 날짜 분할 테이블 등). */
    @Test void detailNotFoundRendersNotFoundPage() throws Exception {
        when(service.get(any(LocalDate.class), eq(9L)))
            .thenThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "FIDO 로그 9"));
        mvc.perform(get("/logs/fido/2026-09-25/9").with(user(companyUser)))
            .andExpect(status().isNotFound())
            .andExpect(view().name("error/404"));
    }

    @Test void detailPrettyPrintsJson() throws Exception {
        when(service.get(any(LocalDate.class), eq(5L)))
            .thenReturn(detailOf(5L, "{\"op\":\"Auth\",\"result\":\"1200\"}"));
        when(companies.name(1L)).thenReturn("KB국민은행");
        mvc.perform(get("/logs/fido/2026-09-16/5").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(view().name("log/fido/detail"))
            .andExpect(content().string(containsString("&quot;op&quot; : &quot;Auth&quot;")))
            .andExpect(content().string(containsString("KB국민은행")));
    }

    /** 이전 어드민과 같은 칸: 번호 | 구분 | 서비스명 | 사용자ID | 인증장치 | 로그시간. 구분은 원문 그대로. */
    @Test void listShowsLegacyColumns() throws Exception {
        String longUser = "f5JyUa2Q1lm020DrWpGOnm8/vEmxHLmrOOHRxxxxxxxxxxxxxxxxxxxxxx";
        when(service.search(any(), any())).thenReturn(result(List.of(
            row(1L, "TC", longUser, 2L),
            row(2L, "DeReg", "c95av28e", 512L),
            row(3L, null, null, null)), false));
        String html = mvc.perform(get("/logs/fido").with(user(companyUser)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html)
            .containsSubsequence("<th>번호</th>", "<th>구분</th>", "<th>서비스명</th>", "<th>사용자ID</th>",
                "<th>인증장치</th>", "<th>로그시간</th>")
            .contains(">TC<").contains(">DeReg<")
            .contains("com.kbstar.kbbank")
            .contains(">지문<").contains(">없음<").contains(">알수없음<")
            .contains("title=\"" + longUser + "\"")
            .contains(longUser.substring(0, 37) + "...")
            .contains("2026-10-02 14:42:20")
            .contains("name=\"op\"").contains("name=\"userid\"").contains("name=\"servicename\"")
            .contains("value=\"TC\"").contains("value=\"DeReg\"")
            .doesNotContain("name=\"outcome\"").doesNotContain("name=\"serialcode\"")
            .doesNotContain("앞 50,000건");
    }

    @Test void truncatedScanShowsNotice() throws Exception {
        when(service.search(any(), any())).thenReturn(result(List.of(row(1L)), true));
        mvc.perform(get("/logs/fido").param("op", "Auth").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("앞 50,000건")));
    }

    /** 운영 로그는 base64url 이다. 상세는 디코드한 JSON 을 정리해 보여 주고 풀어낸 값을 위에 둔다. */
    @Test void detailDecodesBase64Payload() throws Exception {
        String json = "{\"transaction\":{\"serviceName\":\"com.kbstar.kbbank\",\"userName\":\"u1\",\"op\":\"TC\",\"bioType\":2,\"status\":\"Success\"}}";
        String encoded = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(service.get(any(LocalDate.class), eq(6L))).thenReturn(detailOf(6L, encoded));
        when(companies.name(1L)).thenReturn("KB국민은행");
        mvc.perform(get("/logs/fido/2026-09-16/6").with(user(companyUser)))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("&quot;userName&quot; : &quot;u1&quot;")))
            .andExpect(content().string(containsString("com.kbstar.kbbank")))
            .andExpect(content().string(containsString(">지문<")))
            .andExpect(content().string(containsString(">Success<")))
            .andExpect(content().string(not(containsString(encoded))));
    }
}
