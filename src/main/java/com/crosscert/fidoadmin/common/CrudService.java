package com.crosscert.fidoadmin.common;

import lombok.extern.slf4j.Slf4j;
import com.crosscert.fidoadmin.audit.AuditChanges;
import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.function.Consumer;
import org.springframework.core.GenericTypeResolver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

/**
 * 화면용 CRUD 공통 처리: 테넌트 필터·검사, 기본값, 타임스탬프, 감사 로그.
 * 하위 클래스는 @Service 를 붙이고 훅만 구현한다.
 */
@Slf4j
public abstract class CrudService<E, ID, S extends SearchForm> {

    protected final AdminRepository<E, ID> repository;
    protected final AuditLogger audit;
    protected final TenantContext tenant;
    /** E 의 실제 클래스. {@link #listExcludedProperties} 를 쓰는 부분 조회가 메타모델을 찾는 데 쓴다. */
    private final Class<E> entityType;

    @SuppressWarnings("unchecked")
    protected CrudService(AdminRepository<E, ID> repository, AuditLogger audit, TenantContext tenant) {
        this.repository = repository;
        this.audit = audit;
        this.tenant = tenant;
        Class<?>[] args = GenericTypeResolver.resolveTypeArguments(getClass(), CrudService.class);
        this.entityType = args == null ? null : (Class<E>) args[0];
    }

    // ---- 훅 ----
    protected abstract Specification<E> toSpecification(S form);
    /** COMPANY_IDX 에 해당하는 엔티티 속성명. 없으면 null (테넌트 필터 없음, SUPER 전용 화면). */
    protected abstract String companyIdxAttribute();
    protected abstract Long companyIdxOf(E entity);
    protected abstract void setCompanyIdx(E entity, Long companyIdx);
    public abstract String idOf(E entity);
    protected abstract String tableName();
    public Sort defaultSort() { return Sort.by(Sort.Direction.DESC, "idx"); }
    /**
     * 정렬을 허용할 속성 이름. 기본은 PK 뿐이며, 화면에서 필요한 컬럼을 더한다.
     * 목록에 없는 값이 들어오면 기본 정렬로 되돌아간다(매핑되지 않은 속성으로 인한 500 방지).
     */
    public java.util.Set<String> sortableProperties() { return java.util.Set.of("idx"); }
    /**
     * 목록에서 읽지 않을 엔티티 속성(CLOB 필드). Hibernate 는 {@code @Lob String} 을 항상 즉시 읽어,
     * 목록 DTO 가 버리는 값을 행마다 전송한다. 여기 적은 속성은 SELECT 절에서 빠지고 목록 행에서 null 이다
     * ({@link PartialFetch}). 목록 DTO 가 그 속성을 쓰지 않는 화면만 선언한다. 상세({@link #get})는 영향이 없다.
     */
    protected java.util.Set<String> listExcludedProperties() { return java.util.Set.of(); }
    protected void applyDefaults(E entity) {}
    protected void touchCreated(E entity, LocalDateTime now) {}
    protected void touchUpdated(E entity, LocalDateTime now) {}
    protected void beforeDelete(E entity) {}

    /**
     * 등록·수정·삭제가 끝난 뒤(같은 트랜잭션 안) 부른다. 기본은 아무것도 하지 않는다.
     * FIDO 서버가 읽는 데이터를 다루는 서비스가 reload 이벤트를 발행하는 자리다.
     */
    protected void afterChange(String action, E entity) {}

    // ---- 공개 API ----
    /**
     * COMPANY_IDX 가 없는 테이블(메타·시스템 정보 등)은 SUPER 전용이다(설계 3.3).
     * SecurityConfig 의 URL 허용 목록만으로는 새 화면이 추가될 때 누락될 수 있어
     * 서비스 계층에서도 막는다.
     */
    protected void requireSuperForGlobalTable() {
        if (companyIdxAttribute() == null && !tenant.require().isSuper()) {
            throw new org.springframework.security.access.AccessDeniedException(
                tableName() + " 은 최고 관리자 전용입니다");
        }
    }

    @Transactional(readOnly = true)
    public Page<E> search(S form, Pageable pageable) {
        Specification<E> spec = listSpecification(form);
        java.util.Set<String> excluded = listExcludedProperties();
        if (excluded.isEmpty()) return repository.findAll(spec, pageable);
        if (!(repository instanceof PartialFetch)) {
            throw new IllegalStateException(tableName() + " 리포지터리가 PartialFetch 를 직접 상속해야 목록에서 "
                + excluded + " 을(를) 뺄 수 있습니다");
        }
        @SuppressWarnings("unchecked")
        PartialFetch<E> partial = (PartialFetch<E>) repository;
        return partial.findAllExcluding(entityType, spec, pageable, excluded);
    }

    /** 목록 조건 = 화면 조건 + 유효 테넌트. SUPER 전용 검사도 여기서 한다. */
    protected Specification<E> listSpecification(S form) {
        requireSuperForGlobalTable();
        Specification<E> spec = toSpecification(form);
        String attr = companyIdxAttribute();
        if (attr != null) {
            // 역할을 묻지 않는다. 유효 테넌트가 곧 경계다.
            spec = Specs.all(spec, Specs.eq(attr, tenant.companyIdx()));
        }
        return spec == null ? Specs.all() : spec;
    }

    @Transactional(readOnly = true)
    public E get(ID id) {
        requireSuperForGlobalTable();
        E e = repository.findById(id).orElseThrow(() -> new EntityNotFoundException(tableName() + " " + id));
        checkTenant(e);
        return e;
    }

    /**
     * 신규 엔티티 저장. 기본은 repository.save(). 할당형 PK 테이블은
     * {@link AssignedIdCrudService} 가 존재 검사 + persist 로 바꾼다
     * (save() 는 식별자가 있으면 MERGE 로 동작해 기존 행을 덮어쓴다).
     */
    protected E insert(E entity) { return repository.save(entity); }

    @Transactional
    public E create(E entity) {
        requireSuperForGlobalTable();
        if (companyIdxAttribute() != null) {
            setCompanyIdx(entity, tenant.companyIdx());
        }
        applyDefaults(entity);
        touchCreated(entity, LocalDateTime.now());
        E saved = insert(entity);
        audit.log(AuditType.CREATE, tableName() + " CREATE " + idOf(saved));
        log.info("{} CREATE id={}", tableName(), idOf(saved));
        afterChange("CREATE", saved);
        return saved;
    }

    @Transactional
    public E update(ID id, Consumer<E> mutator) {
        E e = get(id);
        // 변경 전 값을 떠 둔다. mutator 가 같은 객체를 고치므로 이 시점이어야 한다.
        var before = AuditChanges.snapshot(e);
        mutator.accept(e);
        if (companyIdxAttribute() != null) {
            setCompanyIdx(e, tenant.companyIdx());
        }
        touchUpdated(e, LocalDateTime.now());
        E saved = repository.save(e);
        AuditChanges.record(audit, tableName(), idOf(saved), before, saved);
        log.info("{} UPDATE id={}", tableName(), idOf(saved));
        afterChange("UPDATE", saved);
        return saved;
    }

    @Transactional
    public void delete(ID id) {
        E e = get(id);
        beforeDelete(e);
        repository.delete(e);
        audit.log(AuditType.DELETE, tableName() + " DELETE " + idOf(e));
        log.info("{} DELETE id={}", tableName(), idOf(e));
        afterChange("DELETE", e);
    }

    protected void checkTenant(E e) {
        if (companyIdxAttribute() == null) return;
        Long owner = companyIdxOf(e);
        if (owner == null || !owner.equals(tenant.companyIdx())) {
            throw new TenantMismatchException(tableName() + " " + idOf(e));
        }
    }
}
