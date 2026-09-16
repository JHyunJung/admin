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
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/error/**", "/webjars/**", "/css/**", "/js/**", "/favicon.ico").permitAll()
                .requestMatchers("/companies/**", "/licenses/**", "/managers/**", "/system/**").hasRole("SUPER")
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
