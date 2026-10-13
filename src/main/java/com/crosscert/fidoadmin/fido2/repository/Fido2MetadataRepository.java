package com.crosscert.fidoadmin.fido2.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.common.PartialFetch;
import com.crosscert.fidoadmin.fido2.entity.Fido2Metadata;

public interface Fido2MetadataRepository extends AdminRepository<Fido2Metadata, Long>, PartialFetch<Fido2Metadata> { // CLOB 을 빼고 읽는 목록(PartialFetch)은 직접 상속해야 조각이 붙는다
}
