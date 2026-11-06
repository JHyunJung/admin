package com.crosscert.fidoadmin

import org.slf4j.LoggerFactory
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class FidoAdminApplication {

    companion object {
        private val log = LoggerFactory.getLogger(FidoAdminApplication::class.java)

        @JvmStatic
        fun main(args: Array<String>) {
            val ctx = SpringApplication.run(FidoAdminApplication::class.java, *args)
            // 어느 설정으로 떴는지 로그 파일 첫머리에서 바로 알 수 있게. 프로필이 없으면 logback 은 prod 와 같게 동작한다.
            val env = ctx.environment
            log.info("fido-admin 기동: profiles={} logPath={}",
                if (env.activeProfiles.isEmpty()) "(없음 → prod 규칙)" else env.activeProfiles.joinToString(","),
                env.getProperty("logging.file.path", "./logs"))
        }
    }
}
