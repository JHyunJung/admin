package com.crosscert.fidoadmin.system;

import static org.assertj.core.api.Assertions.assertThat;

import com.crosscert.fidoadmin.system.web.FidoSettingKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class FidoSettingKeyTest {

    /** PROP_KEY 는 VARCHAR2(128) 이고 BYTE 의미다. 키는 ASCII 뿐이라 길이=바이트다. */
    @Test void 키는_128바이트를_넘지_않는다() {
        assertThat(FidoSettingKey.values()).allSatisfy(k ->
            assertThat(k.key().getBytes(StandardCharsets.UTF_8).length)
                .as("%s 의 PROP_KEY 길이", k.name())
                .isLessThanOrEqualTo(128));
    }

    /** 같은 키가 둘이면 한쪽 저장이 다른 쪽을 덮어쓴다. */
    @Test void 키가_중복되지_않는다() {
        var keys = Arrays.stream(FidoSettingKey.values()).map(FidoSettingKey::key).collect(Collectors.toSet());
        assertThat(keys).hasSize(FidoSettingKey.values().length);
    }

    /**
     * SystemPropForm 의 @Pattern 과 같은 문자 집합만 쓴다. 이 키들도 결국
     * {@code /system/props/{key}@{companyIdx}} 경로에 나타날 수 있기 때문이다.
     */
    @Test void 키는_URL_안전한_문자만_쓴다() {
        assertThat(FidoSettingKey.values()).allSatisfy(k ->
            assertThat(k.key()).as("%s", k.name()).matches("[A-Za-z0-9._-]+"));
    }

    @Test void 세_섹션이_모두_존재한다() {
        assertThat(Arrays.stream(FidoSettingKey.values()).map(FidoSettingKey::section).distinct())
            .containsExactlyInAnyOrder("인증서 설정", "FIDO 부가기능 설정", "알림메일 설정");
    }

    /** 기본값이 없으면 화면이 빈칸으로 뜨고 저장 시 의도치 않은 값이 들어간다. */
    @Test void 모든_키에_기본값이_있다() {
        assertThat(FidoSettingKey.values()).allSatisfy(k ->
            assertThat(k.defaultValue()).as("%s 의 기본값", k.name()).isNotNull());
    }

    @Test void 토글은_Y_또는_N_이_기본값이다() {
        assertThat(FidoSettingKey.values())
            .filteredOn(k -> k.type() == FidoSettingKey.Type.TOGGLE)
            .allSatisfy(k -> assertThat(k.defaultValue()).isIn("Y", "N"));
    }
}
