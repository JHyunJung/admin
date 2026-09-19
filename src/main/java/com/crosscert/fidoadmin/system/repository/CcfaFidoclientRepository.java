package com.crosscert.fidoadmin.system.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.system.entity.CcfaFidoclient;
import java.util.Optional;

public interface CcfaFidoclientRepository extends AdminRepository<CcfaFidoclient, String> {

    /**
     * FIDO 서버가 자가등록할 때 보내오는 식별자는 SERVERNAME 하나뿐이다(PK 인 SERVERCODE 가 아니다).
     * 기존 어드민의 updateItem/deleteItem 도 SERVERNAME 으로 행을 찾았으므로 같은 기준을 쓴다.
     *
     * <p>이 테이블은 스키마를 바꿀 수 없어 PK 제약이 실제로 없다. 과거에 쌓인 중복 SERVERNAME 이
     * 있을 수 있으므로 단건이 아니라 First 로 받는다 — 여러 건이어도 예외 없이 가장 먼저 찾은 행을 갱신한다.
     */
    Optional<CcfaFidoclient> findFirstByServernameOrderByCreatedtimeAsc(String servername);
}
