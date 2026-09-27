package com.crosscert.fidoadmin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class FidoAdminApplication {
    public static void main(String[] args) {
        var ctx = SpringApplication.run(FidoAdminApplication.class, args);
        // 어느 설정으로 떴는지 로그 파일 첫머리에서 바로 알 수 있게. 프로필이 없으면 logback 은 prod 와 같게 동작한다.
        var env = ctx.getEnvironment();
        log.info("fido-admin 기동: profiles={} logPath={}",
            env.getActiveProfiles().length == 0 ? "(없음 → prod 규칙)" : String.join(",", env.getActiveProfiles()),
            env.getProperty("logging.file.path", "./logs"));
    }
}
