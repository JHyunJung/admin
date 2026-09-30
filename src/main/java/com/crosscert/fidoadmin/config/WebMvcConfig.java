package com.crosscert.fidoadmin.config;

import com.crosscert.fidoadmin.audit.AuditReadInterceptor;
import com.crosscert.fidoadmin.common.TenantSelectionInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.server.ErrorPage;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final TenantSelectionInterceptor tenantSelection;
    private final AuditReadInterceptor auditRead;

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/error/403").setViewName("error/403");
        registry.addViewController("/error/404").setViewName("error/404");
        registry.addViewController("/error/500").setViewName("error/500");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 비밀번호 만료 세션은 변경 화면에 가둔다. 상태가 없어 빈으로 두지 않는다
        // (@WebMvcTest 가 이 설정을 @Import 하므로 생성자 의존성을 늘리지 않는다).
        registry.addInterceptor(new com.crosscert.fidoadmin.auth.PasswordExpiredInterceptor());

        // /api/svc/** 는 FIDO 서버가 로그인 없이 호출하는 자가등록 경로다. 테넌트를 고를 주체가 없고
        // 대상 테이블(CCFA_FIDOCLIENT)도 전역이라 테넌트 선택을 태우면 안 된다.
        registry.addInterceptor(tenantSelection)
            .excludePathPatterns("/login", "/logout", "/signup", "/select-tenant", "/api/svc/**", "/external/**",
                "/error/**", "/webjars/**", "/css/**", "/js/**", "/fonts/**",
                "/favicon.ico", "/favicon.png", "/favicon-*.ico", "/favicon-*.png");

        // 화면 조회 기록. 정적 자원과 인증 경로는 남길 조회가 아니다.
        // /api/** 는 화면이 아니라 서버 간 호출이라 뺀다(자가등록 경로 포함).
        registry.addInterceptor(auditRead)
            .excludePathPatterns(
                "/login", "/logout", "/signup", "/error", "/error/**",
                "/api/**", "/external/**", "/webjars/**", "/css/**", "/js/**",
                "/fonts/**", "/favicon*");
    }

    @Bean
    public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> errorPages() {
        return factory -> factory.addErrorPages(
            new ErrorPage(HttpStatus.FORBIDDEN, "/error/403"),
            new ErrorPage(HttpStatus.NOT_FOUND, "/error/404"),
            new ErrorPage(HttpStatus.INTERNAL_SERVER_ERROR, "/error/500"));
    }
}
