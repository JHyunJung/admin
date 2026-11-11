package com.crosscert.fidoadmin.common

import com.crosscert.fidoadmin.audit.AuditChanges
import com.crosscert.fidoadmin.audit.AuditLogger
import com.crosscert.fidoadmin.audit.AuditType
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import jakarta.persistence.EntityNotFoundException
import java.time.Duration
import java.time.LocalDateTime
import java.util.function.Consumer
import org.slf4j.LoggerFactory
import org.springframework.core.GenericTypeResolver
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.support.PageableExecutionUtils
import org.springframework.security.access.AccessDeniedException
import org.springframework.transaction.annotation.Transactional

/**
 * 화면용 CRUD 공통 처리: 테넌트 필터·검사, 기본값, 타임스탬프, 감사 로그.
 * 하위 클래스는 @Service 를 붙이고 훅만 구현한다.
 *
 * Java 하위 클래스가 `repository`·`audit`·`tenant` 를 필드로 읽으므로 [JvmField] 로 둔다.
 * 하위 클래스의 프록시(@Transactional)가 가로챌 수 있도록 공개·보호 메서드는 모두 `open` 이다.
 */
abstract class CrudService<E : Any, ID : Any, S : SearchForm> protected constructor(
    @JvmField protected val repository: AdminRepository<E, ID>,
    @JvmField protected val audit: AuditLogger,
    @JvmField protected val tenant: TenantContext,
) {

    /** E 의 실제 클래스. [listExcludedProperties] 를 쓰는 부분 조회가 메타모델을 찾는 데 쓴다. */
    private val entityType: Class<E>?

    /** (테넌트, 검색 조건) → 총 건수. [countCacheTtl] 이 0 이면 만들지 않는다. */
    @Volatile
    private var counts: Cache<String, Long>? = null

    init {
        val args = GenericTypeResolver.resolveTypeArguments(javaClass, CrudService::class.java)
        @Suppress("UNCHECKED_CAST")
        entityType = if (args == null) null else args[0] as Class<E>?
    }

    // ---- 훅 ----
    protected abstract fun toSpecification(form: S): Specification<E>?
    /** COMPANY_IDX 에 해당하는 엔티티 속성명. 없으면 null (테넌트 필터 없음, SUPER 전용 화면). */
    protected abstract fun companyIdxAttribute(): String?
    protected abstract fun companyIdxOf(entity: E): Long?
    protected abstract fun setCompanyIdx(entity: E, companyIdx: Long?)
    abstract fun idOf(entity: E): String?
    protected abstract fun tableName(): String
    open fun defaultSort(): Sort = Sort.by(Sort.Direction.DESC, "idx")
    /**
     * 정렬을 허용할 속성 이름. 기본은 PK 뿐이며, 화면에서 필요한 컬럼을 더한다.
     * 목록에 없는 값이 들어오면 기본 정렬로 되돌아간다(매핑되지 않은 속성으로 인한 500 방지).
     */
    open fun sortableProperties(): Set<String> = setOf("idx")
    /**
     * 목록에서 읽지 않을 엔티티 속성(CLOB 필드). Hibernate 는 `@Lob String` 을 항상 즉시 읽어,
     * 목록 DTO 가 버리는 값을 행마다 전송한다. 여기 적은 속성은 SELECT 절에서 빠지고 목록 행에서 null 이다
     * ([PartialFetch]). 목록 DTO 가 그 속성을 쓰지 않는 화면만 선언한다. 상세([get])는 영향이 없다.
     */
    protected open fun listExcludedProperties(): Set<String> = setOf()
    /**
     * 목록 총 건수를 이 시간만큼 재사용한다. 기본 0(매번 센다). 행이 많은 로그 목록은 페이지를 넘길 때마다
     * 같은 조건으로 COUNT(*) 를 다시 보내는데, 그 비용이 본문 조회보다 크다. 켜면 "총 N건" 과 마지막 페이지가
     * 이 시간만큼 늦게 반영된다(본문 행은 매번 DB 에서 읽는다). 켜는 서비스의 리포지터리는 [PartialFetch] 를
     * 직접 상속하고, 검색 폼은 모든 조건을 `extraParams()` 에 넣어야 한다([SearchForm.conditionKey]).
     */
    protected open fun countCacheTtl(): Duration = Duration.ZERO
    protected open fun applyDefaults(entity: E) {}
    protected open fun touchCreated(entity: E, now: LocalDateTime) {}
    protected open fun touchUpdated(entity: E, now: LocalDateTime) {}
    protected open fun beforeDelete(entity: E) {}

    /**
     * 등록·수정·삭제가 끝난 뒤(같은 트랜잭션 안) 부른다. 기본은 아무것도 하지 않는다.
     * FIDO 서버가 읽는 데이터를 다루는 서비스가 reload 이벤트를 발행하는 자리다.
     */
    protected open fun afterChange(action: String, entity: E) {}

    /**
     * 같은 패키지의 [CrudController]·[ReadOnlyController] 가 감사 기록(조회 대상 테이블 이름)에 쓴다.
     * Java 의 protected 는 같은 패키지 접근을 포함했지만 Kotlin 은 그렇지 않아, [tableName] 의 가시성을
     * 바꾸지 않고(하위 클래스가 protected 로 오버라이드한다) 이 통로를 둔다.
     */
    internal fun tableNameForAudit(): String? = tableName()

    // ---- 공개 API ----
    /**
     * COMPANY_IDX 가 없는 테이블(메타·시스템 정보 등)은 SUPER 전용이다.
     * SecurityConfig 의 URL 허용 목록만으로는 새 화면이 추가될 때 누락될 수 있어
     * 서비스 계층에서도 막는다.
     */
    protected open fun requireSuperForGlobalTable() {
        if (companyIdxAttribute() == null && !tenant.require().isSuper) {
            throw AccessDeniedException(tableName() + " 은 최고 관리자 전용입니다")
        }
    }

    @Transactional(readOnly = true)
    open fun search(form: S, pageable: Pageable): Page<E> {
        val spec = listSpecification(form)
        val excluded = listExcludedProperties()
        val cacheCount = !countCacheTtl().isZero
        // Java 판은 리포지터리 결과를 그대로 돌려줬다(목 리포지터리가 null 을 돌려도 통과). Kotlin 이 붙이는
        // 널 검사로 동작이 갈리지 않게 결과를 검사 없이 넘긴다.
        if (excluded.isEmpty() && !cacheCount) return unchecked(repository.findAll(spec, pageable) as Any?)
        if (repository !is PartialFetch<*>) {
            throw IllegalStateException(tableName() + " 리포지터리가 PartialFetch 를 직접 상속해야 목록에서 "
                + excluded + " 을(를) 빼거나 건수를 캐시할 수 있습니다")
        }
        @Suppress("UNCHECKED_CAST")
        val partial = repository as PartialFetch<E>
        if (!cacheCount) return partial.findAllExcluding(entityType!!, spec, pageable, excluded)
        val content = partial.findContentExcluding(entityType!!, spec, pageable, excluded)
        // 키에 테넌트를 넣는다. 조건이 같아도 고객사가 다르면 다른 건수다.
        val key = (if (companyIdxAttribute() == null) "-" else tenant.companyIdx()).toString() + "|" + form.conditionKey()
        return PageableExecutionUtils.getPage(content, pageable) { countCache().get(key) { repository.count(spec) } }
    }

    private fun countCache(): Cache<String, Long> {
        counts?.let { return it }
        synchronized(this) {
            counts?.let { return it }
            val c: Cache<String, Long> = Caffeine.newBuilder().expireAfterWrite(countCacheTtl()).maximumSize(1_000).build()
            counts = c
            return c
        }
    }

    /** 목록 조건 = 화면 조건 + 유효 테넌트. SUPER 전용 검사도 여기서 한다. */
    protected open fun listSpecification(form: S): Specification<E> {
        requireSuperForGlobalTable()
        var spec = toSpecification(form)
        val attr = companyIdxAttribute()
        if (attr != null) {
            // 역할을 묻지 않는다. 유효 테넌트가 곧 경계다.
            spec = Specs.all(spec, Specs.eq(attr, tenant.companyIdx()))
        }
        return spec ?: Specs.all()
    }

    @Transactional(readOnly = true)
    open fun get(id: ID): E {
        requireSuperForGlobalTable()
        val e = repository.findById(id).orElseThrow { EntityNotFoundException(tableName() + " " + id) }
        checkTenant(e)
        return e
    }

    /**
     * 신규 엔티티 저장. 기본은 repository.save(). 할당형 PK 테이블은
     * [AssignedIdCrudService] 가 존재 검사 + persist 로 바꾼다
     * (save() 는 식별자가 있으면 MERGE 로 동작해 기존 행을 덮어쓴다).
     */
    protected open fun insert(entity: E): E = repository.save(entity)

    @Transactional
    open fun create(entity: E): E {
        requireSuperForGlobalTable()
        if (companyIdxAttribute() != null) {
            setCompanyIdx(entity, tenant.companyIdx())
        }
        applyDefaults(entity)
        touchCreated(entity, LocalDateTime.now())
        val saved = insert(entity)
        auditLog(AuditType.CREATE, tableName() + " CREATE " + idOf(saved))
        log.info("{} CREATE id={}", tableName(), idOf(saved))
        afterChange("CREATE", saved)
        return saved
    }

    @Transactional
    open fun update(id: ID, mutator: Consumer<E>): E {
        val e = get(id)
        lockForUpdate(e)
        // 변경 전 값을 떠 둔다. mutator 가 같은 객체를 고치므로 이 시점이어야 한다.
        val before = AuditChanges.snapshot(e)
        mutator.accept(e)
        if (companyIdxAttribute() != null) {
            setCompanyIdx(e, tenant.companyIdx())
        }
        touchUpdated(e, LocalDateTime.now())
        val saved = repository.save(e)
        AuditChanges.record(audit, tableName(), idOf(saved), before, saved, isGlobalTable())
        log.info("{} UPDATE id={}", tableName(), idOf(saved))
        afterChange("UPDATE", saved)
        return saved
    }

    @Transactional
    open fun delete(id: ID) {
        val e = get(id)
        beforeDelete(e)
        repository.delete(e)
        auditLog(AuditType.DELETE, tableName() + " DELETE " + idOf(e))
        log.info("{} DELETE id={}", tableName(), idOf(e))
        afterChange("DELETE", e)
    }

    /**
     * [update] 가 행을 읽은 직후, 변경 전 스냅숏과 mutator 보다 먼저 부르는 자리. 기본은 아무것도 하지 않는다.
     * 다른 경로(로그인 등)가 같은 행을 동시에 고치는 테이블은 여기서 행을 잠그고 다시 읽는다.
     */
    protected open fun lockForUpdate(e: E) {}

    /** 고객사 구분이 없는 테이블인가([companyIdxAttribute] 가 null). */
    protected fun isGlobalTable(): Boolean = companyIdxAttribute() == null

    /**
     * 감사 기록. 전역 테이블 작업은 고객사를 선택한 채 해도 행위자 소속으로 남긴다
     * ([AuditLogger.logGlobal]) — 선택 고객사의 운영자가 다른 고객사·슈퍼매니저의 변경 내용을 읽지 않게 한다.
     */
    protected fun auditLog(type: AuditType, message: String) {
        if (isGlobalTable()) audit.logGlobal(type, message) else audit.log(type, message)
    }

    protected open fun checkTenant(e: E) {
        if (companyIdxAttribute() == null) return
        val owner = companyIdxOf(e)
        if (owner == null || owner != tenant.companyIdx()) {
            throw TenantMismatchException(tableName() + " " + idOf(e))
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(CrudService::class.java)

        /** 값을 널 검사 없이 그대로 넘긴다. Java 메서드 결과를 Java 판과 똑같이 통과시킬 때만 쓴다. */
        @Suppress("UNCHECKED_CAST")
        private fun <T> unchecked(value: Any?): T = value as T

        /** 대용량 로그 목록이 기본으로 쓰는 건수 캐시 유효 시간. */
        @JvmField
        protected val LOG_COUNT_TTL: Duration = Duration.ofSeconds(30)
    }
}
