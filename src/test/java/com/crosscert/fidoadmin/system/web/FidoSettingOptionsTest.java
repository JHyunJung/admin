package com.crosscert.fidoadmin.system.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FidoSettingOptionsTest {

    /** 저장값이 슬라이더 기본 최대(180)보다 크면 최대를 늘린다 — 열고 저장만 해도 값이 깎이면 안 된다. */
    @Test void challengeMaxGrowsToStoredValue() {
        assertThat(FidoSettingOptions.challengeMax("60")).isEqualTo(180);
        assertThat(FidoSettingOptions.challengeMax("300")).isEqualTo(300);
        assertThat(FidoSettingOptions.challengeMax(null)).isEqualTo(180);
        assertThat(FidoSettingOptions.challengeMax("abc")).isEqualTo(180);
        assertThat(FidoSettingOptions.challengeValue("60")).isEqualTo(60);
        assertThat(FidoSettingOptions.challengeValue(null)).isEqualTo(180);
    }

    /** 목록에 없는 저장값은 "N일" 로 끼워 넣어 선택된 채로 보인다. */
    @Test void tcOptionsKeepUnknownStoredValue() {
        assertThat(FidoSettingOptions.tcOptions("9999")).extracting(FidoSettingOptions.Option::label).contains("영구저장");
        assertThat(FidoSettingOptions.tcOptions("9999")).hasSize(7);
        assertThat(FidoSettingOptions.tcOptions("45")).hasSize(8)
            .first().isEqualTo(new FidoSettingOptions.Option("45", "45일"));
        assertThat(FidoSettingOptions.tcOptions(null)).hasSize(7);
    }
}
