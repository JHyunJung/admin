package com.crosscert.fidoadmin.log.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FidoLogJsonTest {

    /** 아는 구분은 한글 표시어로, 모르는 값은 원문 그대로, 없으면 null. */
    @Test void opLabelMapsKnownOpsAndPassesUnknownThrough() {
        assertThat(FidoLogJson.opLabel("Reg")).isEqualTo("등록");
        assertThat(FidoLogJson.opLabel("Auth")).isEqualTo("인증");
        assertThat(FidoLogJson.opLabel("Dereg")).isEqualTo("해지");
        assertThat(FidoLogJson.opLabel("Whatever")).isEqualTo("Whatever");
        assertThat(FidoLogJson.opLabel(null)).isNull();
        assertThat(FidoLogJson.opLabel(" ")).isNull();
    }

    /** 성공은 1200 하나뿐이다. 코드가 없는(파싱 안 되는) 행은 성공이 아니다. */
    @Test void onlySuccessCodeIsSuccess() {
        assertThat(FidoLogJson.isSuccess("1200")).isTrue();
        assertThat(FidoLogJson.isSuccess("1491")).isFalse();
        assertThat(FidoLogJson.isSuccess(null)).isFalse();
    }

    /** 검색 필터는 아는 값만 통과시킨다. 그 밖은 "조건 없음"(null)이라 SQL 에 들어가지 않는다. */
    @Test void filtersAcceptOnlyKnownValues() {
        assertThat(FidoLogJson.outcomeOrNull("success")).isEqualTo("success");
        assertThat(FidoLogJson.outcomeOrNull("fail")).isEqualTo("fail");
        assertThat(FidoLogJson.outcomeOrNull("")).isNull();
        assertThat(FidoLogJson.outcomeOrNull("x' OR 1=1")).isNull();
        assertThat(FidoLogJson.opOrNull("Auth")).isEqualTo("Auth");
        assertThat(FidoLogJson.opOrNull("auth")).isNull();
        assertThat(FidoLogJson.opOrNull(null)).isNull();
    }

    /** SQL 조각에는 고정 상수만 들어간다. */
    @Test void jsonValueUsesFixedPath() {
        assertThat(FidoLogJson.jsonValue("JSONDATA", FidoLogJson.OP_PATH))
            .isEqualTo("JSON_VALUE(JSONDATA, '$.op')");
    }
}
