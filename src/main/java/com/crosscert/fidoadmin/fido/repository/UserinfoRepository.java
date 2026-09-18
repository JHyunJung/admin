package com.crosscert.fidoadmin.fido.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.fido.entity.Userinfo;
import com.crosscert.fidoadmin.fido.service.UserAccountRow;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserinfoRepository extends AdminRepository<Userinfo, Long> {
    long countByCompanyIdx(Long companyIdx);

    /**
     * 사용자 단위 목록. USERINFO 한 행은 기기 하나이므로 {@code (USERID, SERVICENAME)} 으로 묶는다.
     *
     * <p><b>companyIdx 는 반드시 유효 테넌트여야 한다.</b> 이 질의는 CrudService 를 거치지 않아
     * 테넌트 경계가 여기 말고는 없다 — 호출부가 값을 잘못 넘기면 다른 고객사의 사용자가 보인다.
     * {@code UserAccountServiceTest} 가 이 경계를 고정한다.
     *
     * <p>userid·servicename 은 부분 일치다. null 이면 그 조건을 걸지 않는다.
     */
    @Query("""
        select new com.crosscert.fidoadmin.fido.service.UserAccountRow(
            u.userid, u.servicename, count(u), sum(case when u.status = 'O' then 1L else 0L end), max(u.regtime))
        from Userinfo u
        where u.companyIdx = :companyIdx
          and (:userid is null or lower(u.userid) like lower(concat('%', :userid, '%')))
          and (:servicename is null or lower(u.servicename) like lower(concat('%', :servicename, '%')))
        group by u.userid, u.servicename
        order by max(u.regtime) desc, u.userid asc
        """)
    List<UserAccountRow> findUserAccounts(@Param("companyIdx") Long companyIdx,
                                          @Param("userid") String userid,
                                          @Param("servicename") String servicename,
                                          Pageable pageable);

    /**
     * 사용자 단위 총 건수. 페이지네이션이 전체 쪽수를 알려면 그룹의 개수가 필요하다.
     *
     * <p>네이티브 질의인 이유: 그룹의 <b>개수</b>를 세려면 {@code from} 절에 서브질의가
     * 필요한데 JPQL 은 그것을 허용하지 않는다. {@code count(distinct concat(...))} 로
     * 우회할 수도 있지만, 값에 구분자가 섞이면 서로 다른 조합이 같은 문자열이 되어
     * 건수가 틀린다. 정확도를 택했다.
     */
    @Query(value = """
        select count(*) from (
            select 1 from USERINFO
            where COMPANY_IDX = :companyIdx
              and (:userid is null or lower(USERID) like lower('%' || :userid || '%'))
              and (:servicename is null or lower(SERVICENAME) like lower('%' || :servicename || '%'))
            group by USERID, SERVICENAME)
        """, nativeQuery = true)
    long countUserAccounts(@Param("companyIdx") Long companyIdx,
                           @Param("userid") String userid,
                           @Param("servicename") String servicename);

    /**
     * 한 사용자의 기기 목록. 묶음 키가 두 컬럼이므로 둘 다 일치해야 한다 —
     * userid 만으로 찾으면 다른 서비스의 동명 사용자 기기가 섞인다.
     */
    List<Userinfo> findByCompanyIdxAndUseridAndServicenameOrderByRegtimeDesc(
        Long companyIdx, String userid, String servicename);
}
