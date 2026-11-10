package com.crosscert.fidoadmin.manager.entity

import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** 손으로 쓴 toString() 의 형식과 USER_PW 제외를 고정하는 특성화 테스트. */
class CcfaManagerToStringTest {

    @Test fun formatMatchesAndExcludesUserPw() {
        val e = CcfaManager().apply {
            idx = 1L
            userId = "admin"
            userPw = "SECRET-USERPW-MARKER"
            userNm = "관리자"
            userEmail = "a@b.c"
            userPhone = "010"
            companyIdx = 2L
            status = "활성"
            login = "OFF-LINE"
            blockTime = LocalDateTime.of(2026, 1, 2, 3, 4, 5)
            lastAccess = LocalDateTime.of(2026, 2, 3, 4, 5, 6)
            etc = "memo"
            alramType = "none"
            alramLevel = "0"
            createdtime = LocalDateTime.of(2026, 3, 4, 5, 6, 7)
            updatedtime = LocalDateTime.of(2026, 4, 5, 6, 7, 8)
        }

        val s = e.toString()

        assertThat(s).isEqualTo(
            "CcfaManager(idx=1, userId=admin, userNm=관리자, userEmail=a@b.c, userPhone=010, " +
                "companyIdx=2, status=활성, login=OFF-LINE, blockTime=2026-01-02T03:04:05, " +
                "lastAccess=2026-02-03T04:05:06, etc=memo, alramType=none, alramLevel=0, " +
                "createdtime=2026-03-04T05:06:07, updatedtime=2026-04-05T06:07:08)")
        assertThat(s).doesNotContain("SECRET-USERPW-MARKER").doesNotContain("userPw")
    }

    @Test fun nullFieldsPrintAsNull() {
        assertThat(CcfaManager().toString()).isEqualTo(
            "CcfaManager(idx=null, userId=null, userNm=null, userEmail=null, userPhone=null, " +
                "companyIdx=null, status=null, login=null, blockTime=null, lastAccess=null, " +
                "etc=null, alramType=null, alramLevel=null, createdtime=null, updatedtime=null)")
    }
}
