package com.crosscert.fidoadmin.manager.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CcfaManagerRepository extends AdminRepository<CcfaManager, Long> {
    Optional<CcfaManager> findByUserId(String userId);

    /**
     * 로그인 실패 집계를 계정 단위로 직렬화하기 위한 행 잠금.
     * 잠그지 않으면 동시 실패 요청이 같은 PW_FAIL_CNT 를 읽어 증가분이 유실되거나,
     * 늦게 커밋되는 트랜잭션이 ACCOUNT_LOCK='Y' 를 'N' 으로 덮어쓸 수 있다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from CcfaManager m where m.userId = :userId")
    Optional<CcfaManager> findByUserIdForUpdate(@Param("userId") String userId);

    long countByCompanyIdx(Long companyIdx);
}
