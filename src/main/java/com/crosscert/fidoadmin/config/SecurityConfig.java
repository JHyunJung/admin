package com.crosscert.fidoadmin.config;

import com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler;
import com.crosscert.fidoadmin.auth.LoginFailureHandler;
import com.crosscert.fidoadmin.auth.LoginSuccessHandler;
import com.crosscert.fidoadmin.auth.Sha256PasswordEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.util.AntPathMatcher;

/**
 * 접근 제어는 URL 매처(여기)와 서비스 계층({@code CrudService.requireSuperForGlobalTable}) 두 겹이다.
 * 메서드 보안 어노테이션(@PreAuthorize 등)은 쓰지 않으므로 @EnableMethodSecurity 는 두지 않는다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * SUPER 전용 경로. 필터체인의 매처와 {@link #isSuperPath} 가 같은 배열을 본다 — ArchitectureRulesTest 가
     * MenuRegistry.superOnly 와 교차 검증한다. /signups(복수)는 가입 승인 화면이고 permitAll 인 /signup(단수)과 다르다.
     *
     * <p>/criteria 는 여기 없다. AAID(정책)는 기준 데이터 자체는 전역이지만 화면의 용도가
     * 고객사별 활성/비활성 토글이라 COMPANY 운영자도 쓴다. 고객사 경계는 CriteriaQueryService 가 유효 테넌트로 건다.
     */
    static final String[] SUPER_PATHS = {
        "/companies/**", "/licenses/**", "/managers/**", "/system/**", "/fido2/**", "/signups", "/signups/**"
    };

    private static final AntPathMatcher PATHS = new AntPathMatcher();

    /** 이 경로가 SUPER 전용 매처에 걸리는가. 테스트의 교차 검증용이며 필터체인은 같은 배열을 직접 쓴다. */
    public static boolean isSuperPath(String path) {
        for (String pattern : SUPER_PATHS) {
            if (PATHS.match(pattern, path)) return true;
        }
        return false;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new Sha256PasswordEncoder();
    }

    /**
     * maximumSessions(1) 이 쓰는 SessionRegistry 는 세션 소멸 이벤트를 받아야 항목을 지운다.
     * 이 빈이 없으면 타임아웃·로그아웃으로 사라진 세션 정보가 레지스트리에 계속 쌓인다.
     */
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, LoginSuccessHandler success,
                                           LoginFailureHandler failure, AppLogoutSuccessHandler logout) throws Exception {
        http
            // /api/svc/** 는 FIDO 서버가 기동하면서 자기 URL 을 알려오는 자가등록 경로다.
            // 호출자는 브라우저가 아니라 서버이고 로그인 세션도 토큰도 없으므로 CSRF 검사에서 뺀다.
            .csrf(c -> c.ignoringRequestMatchers("/api/svc/**", "/external/**"))
            .authorizeHttpRequests(auth -> auth
                // favicon 은 로그인 화면에서도 필요하고, 콘텐츠 해시가 붙으면 이름이 favicon-<md5>.ico 가 된다.
                // /signup 은 가입 신청 화면이다. 로그인 전에 열려야 하므로 permitAll 이다.
                // /api/svc/** 는 FIDO 서버가 부르는 경로라 인증 없이 열어 둔다(FidoClientRegistrationController 참고).
                .requestMatchers("/login", "/signup", "/api/svc/**", "/external/**", "/error/**", "/webjars/**", "/css/**", "/js/**", "/fonts/**", "/img/**",
                    "/favicon.ico", "/favicon.png", "/favicon-*.ico", "/favicon-*.png").permitAll()
                // /external/** 는 FIDO 서버가 라이선스를 받아 가는 경로다(ExternalLicenseController).
                // SUPER 전용 경로는 SUPER_PATHS 의 설명을 본다.
                .requestMatchers(SUPER_PATHS).hasRole("SUPER")
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .successHandler(success)
                .failureHandler(failure))
            .logout(l -> l
                .logoutUrl("/logout")
                .logoutSuccessHandler(logout)
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID"))
            .sessionManagement(s -> s
                .sessionFixation().migrateSession()
                .maximumSessions(1)
                .expiredUrl("/login?expired"))
            .headers(h -> h.frameOptions(f -> f.sameOrigin()));
        return http.build();
    }
}
