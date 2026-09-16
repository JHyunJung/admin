package com.crosscert.fidoadmin.audit;

import com.crosscert.fidoadmin.log.entity.CcfaAuditLog;
import com.crosscert.fidoadmin.log.repository.CcfaAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AuditLogWriter {
    private final CcfaAuditLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(CcfaAuditLog row) {
        repository.save(row);
    }
}
