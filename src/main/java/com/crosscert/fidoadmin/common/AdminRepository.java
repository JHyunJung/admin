package com.crosscert.fidoadmin.common;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * 화면용 리포지터리 공통 기반. CLOB 컬럼이 있는 테이블의 리포지터리는 여기에 더해 {@link PartialFetch}
 * 를 <b>직접</b> 상속한다(조각 구현 탐색 규칙 때문에 이 인터페이스에 올릴 수 없다).
 */
@NoRepositoryBean
public interface AdminRepository<E, ID> extends JpaRepository<E, ID>, JpaSpecificationExecutor<E> {
}
