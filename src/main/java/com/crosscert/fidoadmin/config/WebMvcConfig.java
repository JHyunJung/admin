package com.crosscert.fidoadmin.config;

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

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/error/403").setViewName("error/403");
        registry.addViewController("/error/404").setViewName("error/404");
        registry.addViewController("/error/500").setViewName("error/500");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantSelection)
            .excludePathPatterns("/login", "/logout", "/signup", "/select-tenant",
                "/error/**", "/webjars/**", "/css/**", "/js/**", "/fonts/**",
                "/favicon.ico", "/favicon.png", "/favicon-*.ico", "/favicon-*.png");
    }

    @Bean
    public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> errorPages() {
        return factory -> factory.addErrorPages(
            new ErrorPage(HttpStatus.FORBIDDEN, "/error/403"),
            new ErrorPage(HttpStatus.NOT_FOUND, "/error/404"),
            new ErrorPage(HttpStatus.INTERNAL_SERVER_ERROR, "/error/500"));
    }
}
