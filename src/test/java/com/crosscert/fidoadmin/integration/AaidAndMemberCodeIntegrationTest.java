package com.crosscert.fidoadmin.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.fido.entity.Appserver;
import com.crosscert.fidoadmin.fido.entity.Criteria;
import com.crosscert.fidoadmin.fido.service.AppserverService;
import com.crosscert.fidoadmin.fido.service.CriteriaMetadataException;
import com.crosscert.fidoadmin.fido.service.CriteriaQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

/**
 * AAID 등록과 멤버코드 생성을 실제 Oracle 에서 한 번씩 돌린다.
 * 단위 테스트는 저장소와 JDBC 를 목으로 바꾸므로 시퀀스·CLOB·JPA 플러시와 JDBC insert 의 순서는 여기서만 확인된다.
 */
@SpringBootTest
@ActiveProfiles("test")
class AaidAndMemberCodeIntegrationTest extends OracleContainerSupport {

    static final String AAID = "IT99#0001";

    @Autowired CriteriaQueryService criteria;
    @Autowired AppserverService appservers;
    @Autowired JdbcTemplate jdbc;

    @AfterEach void clear() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM CCFA_COMPANY_AAID WHERE AAID = ?", AAID);
        jdbc.update("DELETE FROM CRITERIA WHERE AAID = ?", AAID);
        jdbc.update("DELETE FROM APPSERVER WHERE MEMBER_ID = 'it-member-code'");
    }

    private void login(long companyIdx) {
        var u = new ManagerUserDetails(1L, "it", null, "IT", companyIdx, "c", true, true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    @Test void superRegistersAaidBlockedForEveryCompanyAndRejectsDuplicate() {
        login(0L);
        String json = "{\"aaid\":\"" + AAID + "\",\"keyProtection\":2,\"attestationTypes\":[15879]}";

        Criteria saved = criteria.create(json);

        assertThat(saved.getIdx()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT DBMS_LOB.SUBSTR(JSONDATA, 4000, 1) FROM CRITERIA WHERE IDX = ?",
            String.class, saved.getIdx())).isEqualTo(json);
        assertThat(jdbc.queryForObject("SELECT METAHASH FROM CRITERIA WHERE IDX = ?", String.class, saved.getIdx()))
            .isEqualTo(saved.getMetahash()).hasSize(43);
        Integer companies = jdbc.queryForObject("SELECT COUNT(*) FROM CCFA_COMPANY", Integer.class);
        Integer blocked = jdbc.queryForObject("SELECT COUNT(*) FROM CCFA_COMPANY_AAID WHERE AAID = ?", Integer.class, AAID);
        assertThat(blocked).isEqualTo(companies);

        assertThatThrownBy(() -> criteria.create(json))
            .isInstanceOf(CriteriaMetadataException.class)
            .hasMessage("이미 등록된 AAID 입니다.");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM CRITERIA WHERE AAID = ?", Integer.class, AAID)).isEqualTo(1);
    }

    @Test void appserverCreateStoresGeneratedMemberCode() {
        login(1L);
        Appserver in = new Appserver();
        in.setMemberId("it-member-code");

        Long idx = appservers.create(in).getIdx();

        assertThat(jdbc.queryForObject("SELECT MEMBER_CODE FROM APPSERVER WHERE IDX = ?", String.class, idx))
            .matches("[A-Z0-9]{10}");
    }
}
