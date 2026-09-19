package com.crosscert.fidoadmin.config;

import com.crosscert.fidoadmin.auth.AppLogoutSuccessHandler;
import com.crosscert.fidoadmin.auth.LoginFailureHandler;
import com.crosscert.fidoadmin.auth.LoginSuccessHandler;
import com.crosscert.fidoadmin.auth.Sha256PasswordEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new Sha256PasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, LoginSuccessHandler success,
                                           LoginFailureHandler failure, AppLogoutSuccessHandler logout) throws Exception {
        http
            // /api/svc/** 는 FIDO 서버가 기동하면서 자기 URL 을 알려오는 자가등록 경로다.
            // 호출자는 브라우저가 아니라 서버이고 로그인 세션도 토큰도 없으므로 CSRF 검사에서 뺀다.
            .csrf(c -> c.ignoringRequestMatchers("/api/svc/**"))
            .authorizeHttpRequests(auth -> auth
                // favicon 은 로그인 화면에서도 필요하고, 콘텐츠 해시가 붙으면 이름이 favicon-<md5>.ico 가 된다.
                // /signup 은 가입 신청 화면이다. 로그인 전에 열려야 하므로 permitAll 이다.
                // /api/svc/** 는 기존 어드민과 동일하게 인증 없이 열어 둔다(FidoClientRegistrationController 참고).
                .requestMatchers("/login", "/signup", "/api/svc/**", "/error/**", "/webjars/**", "/css/**", "/js/**", "/fonts/**",
                    "/favicon.ico", "/favicon.png", "/favicon-*.ico", "/favicon-*.png").permitAll()
                // /signups(복수)는 가입 승인 화면이다. 위 permitAll 의 /signup(단수, 신청)과 다르다.
                .requestMatchers("/companies/**", "/licenses/**", "/managers/**", "/system/**",
                                 "/criteria/**", "/fido2/**", "/signups", "/signups/**").hasRole("SUPER")
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
