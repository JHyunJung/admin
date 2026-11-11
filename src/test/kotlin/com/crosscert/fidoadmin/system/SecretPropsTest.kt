package com.crosscert.fidoadmin.system

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * [SecretProps] 의 현재 판별·표시·저장 규칙을 그대로 고정하는 특성 테스트.
 * 기대값은 현재 구현의 실제 결과다.
 */
class SecretPropsTest {

    @Test fun PASSWORD_가_들어간_키는_비밀이다() {
        assertThat(SecretProps.isSecret("SMTP_PASSWORD")).isTrue()
    }

    @Test fun PASSWD_가_들어간_키는_비밀이다() {
        assertThat(SecretProps.isSecret("DB_PASSWD_X")).isTrue()
    }

    @Test fun _PW_로_끝나는_키는_비밀이다() {
        assertThat(SecretProps.isSecret("SMTP_PW")).isTrue()
    }

    @Test fun 정확히_PW_인_키는_비밀이다() {
        assertThat(SecretProps.isSecret("PW")).isTrue()
    }

    @Test fun SECRET_이나_TOKEN_이_들어간_키는_비밀이다() {
        assertThat(SecretProps.isSecret("CLIENT_SECRET")).isTrue()
        assertThat(SecretProps.isSecret("API_TOKEN_VALUE")).isTrue()
    }

    @Test fun 소문자_키도_대문자로_바꿔_판별한다() {
        assertThat(SecretProps.isSecret("smtp_password")).isTrue()
        assertThat(SecretProps.isSecret("smtp_pw")).isTrue()
        assertThat(SecretProps.isSecret("pw")).isTrue()
        assertThat(SecretProps.isSecret("Client_Secret")).isTrue()
        assertThat(SecretProps.isSecret("auth_token")).isTrue()
    }

    @Test fun 비밀이_아닌_키() {
        assertThat(SecretProps.isSecret(null)).isFalse()
        assertThat(SecretProps.isSecret("")).isFalse()
        assertThat(SecretProps.isSecret("PW_FAIL_LIMIT")).isFalse()
        assertThat(SecretProps.isSecret("SMTP_USERNAME")).isFalse()
        assertThat(SecretProps.isSecret("PWD")).isFalse()
        assertThat(SecretProps.isSecret("SMTP_PW_X")).isFalse()
    }

    @Test fun forDisplay_비밀이_아니면_원본을_그대로_준다() {
        assertThat(SecretProps.forDisplay("SMTP_HOST", "mail.example.com")).isEqualTo("mail.example.com")
        assertThat(SecretProps.forDisplay("SMTP_HOST", null)).isNull()
        assertThat(SecretProps.forDisplay("SMTP_HOST", "")).isEqualTo("")
        assertThat(SecretProps.forDisplay(null, "v")).isEqualTo("v")
    }

    @Test fun forDisplay_비밀이고_값이_있으면_마스크를_준다() {
        assertThat(SecretProps.forDisplay("SMTP_PASSWORD", "real")).isEqualTo(SecretProps.MASK)
        assertThat(SecretProps.forDisplay("SMTP_PASSWORD", " ")).isEqualTo("********")
    }

    @Test fun forDisplay_비밀이고_값이_없으면_빈_값을_준다() {
        assertThat(SecretProps.forDisplay("SMTP_PASSWORD", null)).isEqualTo("")
        assertThat(SecretProps.forDisplay("SMTP_PASSWORD", "")).isEqualTo("")
    }

    @Test fun shouldSave_비밀이_아니면_무엇이든_저장한다() {
        assertThat(SecretProps.shouldSave("SMTP_HOST", SecretProps.MASK)).isTrue()
        assertThat(SecretProps.shouldSave("SMTP_HOST", "")).isTrue()
        assertThat(SecretProps.shouldSave("SMTP_HOST", null)).isTrue()
        assertThat(SecretProps.shouldSave(null, null)).isTrue()
    }

    @Test fun shouldSave_비밀이면_빈_값과_마스크는_저장하지_않는다() {
        assertThat(SecretProps.shouldSave("SMTP_PASSWORD", null)).isFalse()
        assertThat(SecretProps.shouldSave("SMTP_PASSWORD", "")).isFalse()
        assertThat(SecretProps.shouldSave("SMTP_PASSWORD", "********")).isFalse()
    }

    @Test fun shouldSave_비밀이고_새_값이면_저장한다() {
        assertThat(SecretProps.shouldSave("SMTP_PASSWORD", "new-secret")).isTrue()
        assertThat(SecretProps.shouldSave("SMTP_PASSWORD", " ")).isTrue()
    }

    /** 앞뒤 공백·개행이 붙어 돌아온 마스크도 미변경으로 본다(진단 106). 그대로 저장하면 비밀번호가 "********\n" 이 된다. */
    @Test fun shouldSave_앞뒤_공백이_붙은_마스크는_저장하지_않는다() {
        assertThat(SecretProps.shouldSave("SMTP_PASSWORD", " ********")).isFalse()
        assertThat(SecretProps.shouldSave("SMTP_PASSWORD", "********\r\n")).isFalse()
    }

    /** 마스크 뒤에 글자를 붙인 값은 새 값인지 실수인지 알 수 없어 거절 대상이다. */
    @Test fun isEditedMask_마스크로_시작하는_변형만_참이다() {
        assertThat(SecretProps.isEditedMask("SMTP_PASSWORD", "********abc")).isTrue()
        assertThat(SecretProps.isEditedMask("SMTP_PASSWORD", " ********abc\n")).isTrue()
        assertThat(SecretProps.isEditedMask("SMTP_PASSWORD", SecretProps.MASK)).isFalse()
        assertThat(SecretProps.isEditedMask("SMTP_PASSWORD", "********\n")).isFalse()
        assertThat(SecretProps.isEditedMask("SMTP_PASSWORD", "abc********")).isFalse()
        assertThat(SecretProps.isEditedMask("SMTP_PASSWORD", null)).isFalse()
        assertThat(SecretProps.isEditedMask("SMTP_HOST", "********abc")).isFalse()
    }
}
