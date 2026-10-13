package com.crosscert.fidoadmin.integration;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.crosscert.fidoadmin.fido.entity.Sign;
import com.crosscert.fidoadmin.fido.repository.SignRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

/**
 * 목록 조회가 CLOB 컬럼을 SELECT 절에서 실제로 빼는지 실제 Oracle 로 확인한다.
 * 단위 테스트는 "findAllExcluding 으로 갔는가" 까지만 보고, 생성된 SQL 과 되돌아온 값은 여기서 본다.
 *
 * <p>EntityBootTest 처럼 로컬 도커 Oracle(local 프로파일)을 쓴다. 질의 모양만 보는 테스트라 시드 데이터에
 * 의존하지 않고, 넣은 행은 @DataJpaTest 트랜잭션이 되돌린다. DB 가 없으면 건너뛴다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("local")
@org.junit.jupiter.api.condition.EnabledIf("com.crosscert.fidoadmin.LocalOracle#isUp")
class PartialFetchIntegrationTest {

    static final long COMPANY = 987_654L;

    @Autowired EntityManager em;
    @Autowired SignRepository signs;

    Logger sqlLogger = (Logger) LoggerFactory.getLogger("org.hibernate.SQL");
    Level previous;
    ListAppender<ILoggingEvent> sql = new ListAppender<>();

    @BeforeEach void captureSql() {
        previous = sqlLogger.getLevel();
        sqlLogger.setLevel(Level.DEBUG);
        sql.start();
        sqlLogger.addAppender(sql);
    }

    @AfterEach void stopCapture() {
        sqlLogger.detachAppender(sql);
        sqlLogger.setLevel(previous);
    }

    private Sign sign(String userid, String plaintext, LocalDateTime at) {
        Sign s = new Sign();
        s.setCompanyIdx(COMPANY); s.setUserid(userid); s.setAssertion("assertion-" + userid);
        s.setDn("CN=" + userid); s.setPlaintext(plaintext); s.setData("data-" + userid); s.setSignature("sig-" + userid);
        s.setMemo("m"); s.setDel("N"); s.setCreatetime(at);
        em.persist(s);
        return s;
    }

    @Test void listReadsEveryColumnExceptTheExcludedClob() {
        Sign older = sign("u1", "x".repeat(10_000), LocalDateTime.of(2026, 1, 1, 10, 0));
        Sign newer = sign("u2", "y".repeat(10_000), LocalDateTime.of(2026, 1, 2, 10, 0));
        em.flush(); em.clear();
        sql.list.clear();

        Specification<Sign> mine = (root, q, cb) -> cb.equal(root.get("companyIdx"), COMPANY);
        Page<Sign> page = signs.findAllExcluding(Sign.class, mine,
            PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "createtime", "idx")), Set.of("plaintext"));

        // 페이지·정렬·건수는 findAll(spec, pageable) 과 같다.
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(1);
        Sign row = page.getContent().get(0);
        assertThat(row.getIdx()).isEqualTo(newer.getIdx());
        assertThat(row.getUserid()).isEqualTo("u2");
        assertThat(row.getAssertion()).isEqualTo("assertion-u2");
        assertThat(row.getDn()).isEqualTo("CN=u2");
        assertThat(row.getCreatetime()).isEqualTo(newer.getCreatetime());
        assertThat(row.getCompanyIdx()).isEqualTo(COMPANY);
        // 제외한 CLOB 만 비어 있다.
        assertThat(row.getPlaintext()).isNull();

        // 생성된 SELECT 에 PLAINTEXT 가 없다(건수 쿼리는 컬럼을 고르지 않는다).
        String selects = String.join("\n", sql.list.stream().map(ILoggingEvent::getFormattedMessage).toList()).toLowerCase(Locale.ROOT);
        assertThat(selects).contains("select");
        assertThat(selects).contains("assertion");
        assertThat(selects).doesNotContain("plaintext");
        assertThat(older.getIdx()).isNotNull();
    }

    /** 건수 캐시 경로: 본문만 읽고 COUNT 를 보내지 않는다. 제외 속성이 없으면 엔티티를 그대로 읽는다. */
    @Test void contentOnlyQueryReadsWholeEntitiesWithoutCounting() {
        sign("u1", "p1", LocalDateTime.of(2026, 1, 1, 10, 0));
        Sign newer = sign("u2", "p2", LocalDateTime.of(2026, 1, 2, 10, 0));
        em.flush(); em.clear();
        sql.list.clear();

        Specification<Sign> mine = (root, q, cb) -> cb.equal(root.get("companyIdx"), COMPANY);
        var rows = signs.findContentExcluding(Sign.class, mine,
            PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "idx")), Set.of());

        assertThat(rows).extracting(Sign::getIdx).containsExactly(newer.getIdx());
        assertThat(rows.get(0).getPlaintext()).isEqualTo("p2");
        String selects = String.join("\n", sql.list.stream().map(ILoggingEvent::getFormattedMessage).toList()).toLowerCase(Locale.ROOT);
        assertThat(selects).doesNotContain("count(");
    }
}
