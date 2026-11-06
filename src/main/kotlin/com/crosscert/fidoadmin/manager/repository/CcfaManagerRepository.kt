package com.crosscert.fidoadmin.manager.repository

import com.crosscert.fidoadmin.common.AdminRepository
import com.crosscert.fidoadmin.manager.entity.CcfaManager
import jakarta.persistence.LockModeType
import java.util.Optional
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/*
 * 파라미터를 nullable 로 둔다. Spring Data 는 Kotlin 리포지토리의 non-null 파라미터에 null 이 오면
 * 쿼리 전에 IllegalArgumentException 을 던지는데, Java 리포지토리는 null 을 그대로 쿼리에 넘겼다.
 */
interface CcfaManagerRepository : AdminRepository<CcfaManager, Long> {
    fun findByUserId(userId: String?): Optional<CcfaManager>

    /**
     * 로그인 실패 집계를 계정 단위로 직렬화하기 위한 행 잠금.
     * 잠그지 않으면 동시 실패 요청이 같은 PW_FAIL_CNT 를 읽어 증가분이 유실되거나,
     * 늦게 커밋되는 트랜잭션이 ACCOUNT_LOCK='Y' 를 'N' 으로 덮어쓸 수 있다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from CcfaManager m where m.userId = :userId")
    fun findByUserIdForUpdate(@Param("userId") userId: String?): Optional<CcfaManager>

    /**
     * 승인·거절에서 대상 행을 잠근다. 잠그지 않으면 두 관리자가 동시에 처리할 때
     * 둘 다 "승인대기" 를 읽어 통과하고, 나중 트랜잭션이 앞선 결과를 덮어쓴다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from CcfaManager m where m.idx = :idx")
    fun findByIdxForUpdate(@Param("idx") idx: Long?): Optional<CcfaManager>

    /** 가입 승인 대기 목록. 오래된 신청이 위로 온다. */
    fun findByStatusOrderByCreatedtimeAsc(status: String?): List<CcfaManager>

    fun countByCompanyIdx(companyIdx: Long?): Long
}
