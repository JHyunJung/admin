package com.crosscert.fidoadmin.auth;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.crosscert.fidoadmin.common.GlobalExceptionHandler;
import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.config.CurrentPathAdvice;
import com.crosscert.fidoadmin.config.SecurityConfig;
import com.crosscert.fidoadmin.config.WebMvcConfig;
import com.crosscert.fidoadmin.dashboard.DashboardController;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerPwPolicyRepository;
import com.crosscert.fidoadmin.manager.repository.CcfaManagerRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * COMPANY_IDX 가 null 인 계정의 로그인 시도가 실제 인증 필터를 통과할 때 어떻게 끝나는지 확인한다.
 *
 * <p>ManagerUserDetails 생성자는 AuthenticationException 이 아닌 IllegalArgumentException 을 던진다.
 * 그대로 새어 나가면 500 오류 페이지가 되고 예외 메시지의 USER_ID 가 노출됐을 것이다.
 * 실제로는 DaoAuthenticationProvider.retrieveUser 의 catch 절이 Exception 전체를 잡아
 * InternalAuthenticationServiceException(AuthenticationException 하위)으로 감싸므로,
 * LoginFailureHandler 를 타고 다른 실패와 같은 {@code /login?error} 로 끝난다.
 *
 * <p>이 결과는 Spring Security 내부 구현에 기댄다. 버전을 올릴 때 조용히 깨지면
 * 500 페이지에 USER_ID 가 노출되므로, 추론으로 두지 않고 테스트로 고정한다.
 */
@WebMvcTest(controllers = DashboardController.class)
@Import({SecurityConfig.class, WebMvcConfig.class, CurrentPathAdvice.class, MenuRegistry.class,
         GlobalExceptionHandler.class, ManagerUserDetailsService.class, LoginFailureHandler.class})
class NullCompanyIdxLoginFlowTest {

    @Autowired MockMvc mvc;

    @MockitoBean CcfaManagerRepository managers;
    @MockitoBean CcfaManagerPwPolicyRepository policies;
    @MockitoBean com.crosscert.fidoadmin.company.repository.CcfaCompanyRepository companies;
    @MockitoBean LoginSuccessHandler success;
    @MockitoBean AppLogoutSuccessHandler logout;
    @MockitoBean com.crosscert.fidoadmin.dashboard.StatisticsQueryService stats;
    @MockitoBean com.crosscert.fidoadmin.company.service.CompanyLookup companyLookup;
    @MockitoBean LoginAttemptService loginAttempts;

    /**
     * COMPANY_IDX 가 null 인 계정으로 로그인하면 500 이 아니라 로그인 화면으로 돌아간다.
     * 슈퍼 관리자로 승격되지 않는 것은 물론이고, 사용자에게 스택 트레이스도 보이지 않는다.
     */
    @Test void nullCompanyIdxLoginEndsAtLoginPageNotServerError() throws Exception {
        CcfaManager ghost = new CcfaManager();
        ghost.setIdx(9L);
        ghost.setUserId("ghost");
        // Sha256PasswordEncoder 로 인코딩된 "Company1234!" 가 아니어도 된다.
        // COMPANY_IDX 검사는 비밀번호 대조보다 먼저 UserDetails 를 만들 때 터진다.
        ghost.setUserPw("whatever");
        ghost.setUserNm("유령");
        ghost.setCompanyIdx(null);
        ghost.setStatus("활성");

        when(managers.findByUserId("ghost")).thenReturn(Optional.of(ghost));
        when(policies.findFirstByUserIdOrderByIdxDesc(anyString())).thenReturn(Optional.empty());

        mvc.perform(post("/login").with(csrf())
                .param("username", "ghost")
                .param("password", "Company1234!"))
            .andExpect(status().is3xxRedirection())
            // 다른 로그인 실패와 구분되지 않는 일반 오류. 계정 존재 여부도, 예외 메시지의
            // USER_ID 도 노출되지 않는다.
            .andExpect(redirectedUrl("/login?error"));
    }
}
