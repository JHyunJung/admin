package com.crosscert.fidoadmin.common;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.function.Consumer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

/**
 * 화면용 CRUD 공통 처리: 테넌트 필터·검사, 기본값, 타임스탬프, 감사 로그.
 * 하위 클래스는 @Service 를 붙이고 훅만 구현한다.
 */
public abstract class CrudService<E, ID, S extends SearchForm> {

    protected final AdminRepository<E, ID> repository;
    protected final AuditLogger audit;

    protected CrudService(AdminRepository<E, ID> repository, AuditLogger audit) {
        this.repository = repository;
        this.audit = audit;
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
    protected void applyDefaults(E entity) {}
    protected void touchCreated(E entity, LocalDateTime now) {}
    protected void touchUpdated(E entity, LocalDateTime now) {}
    protected void beforeDelete(E entity) {}

    // ---- 공개 API ----
    /**
     * COMPANY_IDX 가 없는 테이블(메타·시스템 정보 등)은 SUPER 전용이다(설계 3.3).
     * SecurityConfig 의 URL 허용 목록만으로는 새 화면이 추가될 때 누락될 수 있어
     * 서비스 계층에서도 막는다.
     */
    protected void requireSuperForGlobalTable() {
        if (companyIdxAttribute() == null && !TenantContext.isSuper()) {
            throw new org.springframework.security.access.AccessDeniedException(
                tableName() + " 은 최고 관리자 전용입니다");
        }
    }

    @Transactional(readOnly = true)
    public Page<E> search(S form, Pageable pageable) {
        requireSuperForGlobalTable();
        Specification<E> spec = toSpecification(form);
        String attr = companyIdxAttribute();
        if (attr != null) {
            Long filter = TenantContext.isSuper() ? form.getCompanyIdx() : TenantContext.companyIdx();
            spec = Specs.all(spec, Specs.eq(attr, filter));
        }
        return repository.findAll(spec == null ? Specs.all() : spec, pageable);
    }

    @Transactional(readOnly = true)
    public E get(ID id) {
        requireSuperForGlobalTable();
        E e = repository.findById(id).orElseThrow(() -> new EntityNotFoundException(tableName() + " " + id));
        checkTenant(e);
        return e;
    }

    @Transactional
    public E create(E entity) {
        requireSuperForGlobalTable();
        if (companyIdxAttribute() != null && !TenantContext.isSuper()) {
            setCompanyIdx(entity, TenantContext.companyIdx());
        }
        applyDefaults(entity);
        touchCreated(entity, LocalDateTime.now());
        E saved = repository.save(entity);
        audit.log(AuditType.CREATE, tableName() + " CREATE " + idOf(saved));
        return saved;
    }

    @Transactional
    public E update(ID id, Consumer<E> mutator) {
        E e = get(id);
        mutator.accept(e);
        if (companyIdxAttribute() != null && !TenantContext.isSuper()) {
            setCompanyIdx(e, TenantContext.companyIdx());
        }
        touchUpdated(e, LocalDateTime.now());
        E saved = repository.save(e);
        audit.log(AuditType.UPDATE, tableName() + " UPDATE " + idOf(saved));
        return saved;
    }

    @Transactional
    public void delete(ID id) {
        E e = get(id);
        beforeDelete(e);
        repository.delete(e);
        audit.log(AuditType.DELETE, tableName() + " DELETE " + idOf(e));
    }

    protected void checkTenant(E e) {
        if (companyIdxAttribute() == null || TenantContext.isSuper()) return;
        Long owner = companyIdxOf(e);
        if (owner == null || !owner.equals(TenantContext.companyIdx())) {
            throw new TenantMismatchException(tableName() + " " + idOf(e));
        }
    }
}
