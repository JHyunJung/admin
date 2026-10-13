package com.crosscert.fidoadmin.fido.repository;

import com.crosscert.fidoadmin.common.AdminRepository;
import com.crosscert.fidoadmin.common.PartialFetch;
import com.crosscert.fidoadmin.fido.entity.TransactionConfirmation;

public interface TransactionConfirmationRepository extends AdminRepository<TransactionConfirmation, Long>, PartialFetch<TransactionConfirmation> { // CLOB 을 빼고 읽는 목록(PartialFetch)은 직접 상속해야 조각이 붙는다
}
