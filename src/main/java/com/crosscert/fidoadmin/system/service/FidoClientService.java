package com.crosscert.fidoadmin.system.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.common.AssignedIdCrudService;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.system.entity.CcfaFidoclient;
import com.crosscert.fidoadmin.system.repository.CcfaFidoclientRepository;
import com.crosscert.fidoadmin.system.web.FidoClientSearchForm;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CCFA_FIDOCLIENT — 문자열 PK(SERVERCODE). STATUS 기본 'ON'. SUPER 전용. */
@Service
public class FidoClientService extends AssignedIdCrudService<CcfaFidoclient, String, FidoClientSearchForm> {

    private final CcfaFidoclientRepository fidoClients;

    public FidoClientService(CcfaFidoclientRepository repository, AuditLogger audit, EntityManager em, TenantContext tenant) {
        super(repository, audit, em, tenant);
        this.fidoClients = repository;
    }

    @Override protected Specification<CcfaFidoclient> toSpecification(FidoClientSearchForm f) {
        return Specs.all(
            Specs.like("servercode", f.getServercode()),
            Specs.eq("status", f.getStatus()));
    }
    @Override protected String companyIdxAttribute() { return null; }
    @Override protected Long companyIdxOf(CcfaFidoclient e) { return null; }
    @Override protected void setCompanyIdx(CcfaFidoclient e, Long c) {}
    @Override public String idOf(CcfaFidoclient e) { return e.getServercode(); }
    @Override protected String assignedId(CcfaFidoclient e) { return e.getServercode(); }
    @Override protected String tableName() { return "CCFA_FIDOCLIENT"; }
    @Override public Sort defaultSort() { return Sort.by(Sort.Direction.ASC, "servercode"); }
    @Override public Set<String> sortableProperties() { return Set.of("servercode", "servername", "status", "updatedtime"); }

    @Override protected void applyDefaults(CcfaFidoclient e) {
        if (e.getStatus() == null || e.getStatus().isBlank()) e.setStatus("ON");
    }
    @Override protected void touchCreated(CcfaFidoclient e, LocalDateTime now) { e.setCreatedtime(now); e.setUpdatedtime(now); }
    @Override protected void touchUpdated(CcfaFidoclient e, LocalDateTime now) { e.setUpdatedtime(now); }

    /**
     * FIDO 서버 자가등록. 기동한 FIDO 서버가 스스로 호출한다(로그인 없는 경로).
     *
     * <p>같은 SERVERNAME 이 이미 있으면 그 행을 갱신한다. 기존 어드민은 update 후 insert 를
     * 무조건 실행해 재기동할 때마다 OFF 행과 새 ON 행이 함께 쌓였는데, 그 찌꺼기까지 따라하지는 않는다.
     *
     * <p>테넌트 경계를 타지 않는다 — CCFA_FIDOCLIENT 는 COMPANY_IDX 가 없는 전역 테이블이고,
     * 세션이 없는 이 경로에서 {@code TenantContext.companyIdx()} 를 부르면 예외가 난다.
     * 같은 이유로 감사 로그(행위자 필요)도 남기지 않는다. 호출 기록은 컨트롤러가 애플리케이션 로그에 남긴다.
     */
    @Transactional
    public CcfaFidoclient register(String serverName, String serverUrl) {
        // 존재 검사와 INSERT 사이의 경쟁을 막는다. 이 테이블은 PK 제약이 없어 DB 가 잡아주지 못한다
        // (AssignedIdCrudService.insert 와 같은 방식이다). 잠금은 이 트랜잭션 커밋 때 풀린다.
        em.createNativeQuery("LOCK TABLE " + tableName() + " IN EXCLUSIVE MODE").executeUpdate();

        LocalDateTime now = LocalDateTime.now();
        CcfaFidoclient client = fidoClients.findFirstByServernameOrderByCreatedtimeAsc(serverName)
            .orElseGet(() -> {
                CcfaFidoclient created = new CcfaFidoclient();
                // 보내오는 식별자는 하나뿐이라 SERVERCODE 에도 같은 값을 넣는다.
                // SERVERCODE 는 PK 이자 /system/fido-clients 화면의 URL 경로라 길이를 맞춰 자른다.
                created.setServercode(cut(serverName, 64));
                created.setServername(cut(serverName, 1024));
                created.setCreatedtime(now);
                return created;
            });
        client.setServerurl(cut(serverUrl, 2048));
        client.setStatus("ON");
        client.setUpdatedtime(now);
        return fidoClients.save(client);
    }

    /**
     * FIDO 서버 등록 해제. 행을 지우지 않고 STATUS 만 'OFF' 로 내린다(기존 어드민의 updateItem 과 같다).
     * 등록된 적 없는 서버면 아무것도 하지 않는다.
     */
    @Transactional
    public Optional<CcfaFidoclient> deregister(String serverName) {
        return fidoClients.findFirstByServernameOrderByCreatedtimeAsc(serverName)
            .map(client -> {
                client.setStatus("OFF");
                client.setUpdatedtime(LocalDateTime.now());
                return fidoClients.save(client);
            });
    }

    /** 컬럼 길이를 넘는 값이 와도 등록 자체가 실패하지는 않게 한다. */
    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
