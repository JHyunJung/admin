package com.crosscert.fidoadmin.config;

import com.crosscert.fidoadmin.common.DbConfigCrypto;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class DataSourceConfig {

    @Bean(destroyMethod = "close")
    public HikariDataSource dataSource(Environment environment) {
        // 각 프로필 yml 의 fido-admin.db-config-key(= 환경 변수 DB_CONFIG_KEY)
        DbConfigCrypto crypto = new DbConfigCrypto(
                environment.getProperty("fido-admin.db-config-key")
        );

        String url = crypto.decryptIfEncrypted(
                environment.getRequiredProperty("spring.datasource.url")
        );

        String username = crypto.decryptIfEncrypted(
                environment.getRequiredProperty("spring.datasource.username")
        );

        String password = crypto.decryptIfEncrypted(
                environment.getRequiredProperty("spring.datasource.password")
        );

        HikariDataSource dataSource = new HikariDataSource();

        // 기존 커넥션 풀 설정 반영
        Binder.get(environment).bind(
                "spring.datasource.hikari",
                Bindable.ofInstance(dataSource)
        );

        // 복호화한 접속정보를 마지막에 적용
        dataSource.setDriverClassName(
                environment.getProperty(
                        "spring.datasource.driver-class-name",
                        "oracle.jdbc.OracleDriver"
                )
        );
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);

        return dataSource;
    }
}
