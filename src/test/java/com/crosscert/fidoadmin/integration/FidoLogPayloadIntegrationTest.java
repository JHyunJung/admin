package com.crosscert.fidoadmin.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.log.service.FidoLogQueryService;
import com.crosscert.fidoadmin.log.web.FidoLogRow;
import com.crosscert.fidoadmin.log.web.FidoLogSearchForm;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

/**
 * 운영처럼 base64url 로 인코딩된 JSONDATA(CLOB)를 실제 Oracle 에서 읽어 목록·검색·상세에 풀어 보이는지 확인한다.
 * 스키마에 있는 분할 테이블 FIDO_LOGS_20210101 을 빌려 쓰고 끝나면 지운다.
 */
@SpringBootTest
@ActiveProfiles("test")
class FidoLogPayloadIntegrationTest extends OracleContainerSupport {

    static final LocalDate DAY = LocalDate.of(2021, 1, 1);
    static final String TC_JSON = "{\"transaction\":{\"serviceName\":\"com.kbstar.kbbank\","
        + "\"userName\":\"f5JyUa2Q1lm020DrWpGOnm8/vEmxHLmrOOHR\",\"op\":\"TC\",\"bioType\":2,\"status\":\"Success\"}}";

    @Autowired FidoLogQueryService logs;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach void seed() {
        var u = new ManagerUserDetails(2L, "kbadmin", null, "KB", 1L, "KB", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(TC_JSON.getBytes(StandardCharsets.UTF_8));
        jdbc.update("INSERT INTO FIDO_LOGS_20210101 (IDX, COMPANY_IDX, SERIALCODE, SERVICENAME, JSONDATA, CREATEDTIME) "
            + "VALUES (9001, 1, 'F71oz', 'SERVICE', ?, TIMESTAMP '2021-01-01 14:42:20')", encoded);
        jdbc.update("INSERT INTO FIDO_LOGS_20210101 (IDX, COMPANY_IDX, SERIALCODE, SERVICENAME, JSONDATA, CREATEDTIME) "
            + "VALUES (9002, 1, 'F71oy', 'kbpay', '{\"op\":\"Auth\",\"userid\":\"user132\"}', TIMESTAMP '2021-01-01 14:40:00')");
    }

    @AfterEach void clear() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM FIDO_LOGS_20210101 WHERE IDX IN (9001, 9002)");
    }

    private FidoLogSearchForm form() {
        FidoLogSearchForm f = new FidoLogSearchForm();
        f.setLogDate(DAY);
        return f;
    }

    @Test void listDecodesBase64AndPlainRows() {
        var page = logs.search(form(), PageRequest.of(0, 20)).page();

        assertThat(page.getContent()).extracting(FidoLogRow::idx).containsExactly(9001L, 9002L);
        FidoLogRow tc = page.getContent().get(0);
        assertThat(tc.op()).isEqualTo("TC");
        assertThat(tc.servicename()).isEqualTo("com.kbstar.kbbank");
        assertThat(tc.userid()).isEqualTo("f5JyUa2Q1lm020DrWpGOnm8/vEmxHLmrOOHR");
        assertThat(tc.bioTypeLabel()).isEqualTo("지문");
        FidoLogRow plain = page.getContent().get(1);
        assertThat(plain.servicename()).isEqualTo("kbpay"); // JSON 에 없으면 컬럼 값
        assertThat(plain.userid()).isEqualTo("user132");
    }

    @Test void filterSearchFindsEncodedRow() {
        FidoLogSearchForm f = form();
        f.setOp("tc");
        f.setUserid("f5jyua2q");

        var result = logs.search(f, PageRequest.of(0, 20));

        assertThat(result.truncated()).isFalse();
        assertThat(result.page().getContent()).extracting(FidoLogRow::idx).containsExactly(9001L);
    }

    @Test void detailShowsDecodedJson() {
        var view = logs.get(DAY, 9001L);

        assertThat(view.op()).isEqualTo("TC");
        assertThat(view.status()).isEqualTo("Success");
        assertThat(view.serialcode()).isEqualTo("F71oz");
        assertThat(view.jsondataPretty()).contains("\"userName\" : \"f5JyUa2Q1lm020DrWpGOnm8/vEmxHLmrOOHR\"");
    }
}
