package com.crosscert.fidoadmin.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableJpaRepositories(basePackages = "com.crosscert.fidoadmin")
@EnableTransactionManagement
public class JpaConfig {
}
