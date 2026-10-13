package com.crosscert.fidoadmin.common;

import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/**
 * 일부 속성을 빼고 읽는 목록 조회. Spring Data 조각(fragment)이며 구현은 {@link PartialFetchImpl} 하나다.
 * CLOB 컬럼이 있는 테이블의 리포지터리가 <b>직접</b> 이 인터페이스를 상속한다 — Spring Data 는 조각 구현을
 * 리포지터리 인터페이스가 직접 상속한 인터페이스에서만 찾으므로 {@link AdminRepository} 에 올릴 수 없다.
 *
 * <p>Hibernate 는 {@code @Lob String} 을 바이트코드 보강 없이는 항상 즉시 읽는다. 목록 DTO 가 CLOB 을
 * 싣지 않아도 DB 는 행마다 CLOB 을 보내고 앱은 문자열로 올린 뒤 버린다. 이 조회는 SELECT 절에서
 * 그 컬럼을 빼고, 나머지 속성만 채운 엔티티 인스턴스를 돌려준다.
 *
 * <p>돌려주는 엔티티는 <b>영속 상태가 아니고 제외한 속성이 비어 있다</b>. 목록 행을 그리는 용도로만 쓴다.
 * 결과의 페이지·정렬·총 건수는 {@code findAll(spec, pageable)} 과 같다.
 */
public interface PartialFetch<E> {

    /**
     * @param type     엔티티 클래스(조각 구현은 리포지터리의 도메인 타입을 알 수 없어 넘겨받는다)
     * @param spec     조건. null 이면 전체
     * @param excluded 빼고 읽을 엔티티 속성명(예: CLOB 필드). 비어 있으면 {@code findAll} 과 같다
     */
    Page<E> findAllExcluding(Class<E> type, Specification<E> spec, Pageable pageable, Set<String> excluded);
}
