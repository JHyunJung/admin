package com.crosscert.fidoadmin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.auth.ManagerUserDetails;
import com.crosscert.fidoadmin.common.SelectedTenant;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.log.entity.CcfaAuditLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * {@code AuditLogger.log(AuditType, String)} 이 행위자 소속이 아니라
 * <b>대상 테넌트</b>를 COMPANY_IDX 에 남기는지 확인한다.
 */
class AuditLoggerTenantTest {

    private final AuditLogWriter writer = mock(AuditLogWriter.class);
    private final SelectedTenant selected = new SelectedTenant();
    private final TenantContext tenant = new TenantContext(selected);
    private final CompanyLookup companies = mock(CompanyLookup.class);
    private final AuditLogger logger = new AuditLogger(writer, tenant, companies);
    private final ArgumentCaptor<CcfaAuditLog> captor = ArgumentCaptor.forClass(CcfaAuditLog.class);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void login(long companyIdx) {
        var u = new ManagerUserDetails(1L, "user" + companyIdx, null, "n", companyIdx, "c" + companyIdx, true, true);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
    }

    private void loginSuperSelecting(long targetIdx) {
        var u = new ManagerUserDetails(1L, "superuser", null, "슈퍼관리자", 0L, "SUPER", true, true);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(u, null, u.getAuthorities()));
        selected.select(targetIdx);
        when(companies.name(targetIdx)).thenReturn("고객사" + targetIdx);
    }

    @Test void SUPER_의_행위는_대상_테넌트로_기록된다() {
        loginSuperSelecting(9L);
        logger.log(AuditType.UPDATE, "CCFA_LICENSE UPDATE 1");
        verify(writer).write(captor.capture());
        assertThat(captor.getValue().getCompanyIdx()).isEqualTo(9L);
        assertThat(captor.getValue().getUserId()).isEqualTo("superuser");
    }

    @Test void COMPANY_의_행위는_자기_고객사로_기록된다() {
        login(5L);
        logger.log(AuditType.UPDATE, "CCFA_LICENSE UPDATE 1");
        verify(writer).write(captor.capture());
        assertThat(captor.getValue().getCompanyIdx()).isEqualTo(5L);
    }

    /** 시스템 영역 작업은 선택이 없다. 행위자 소속(0)으로 남긴다. */
    @Test void 미선택_상태의_행위는_행위자_소속으로_기록된다() {
        login(0L);
        logger.log(AuditType.CREATE, "CCFA_COMPANY CREATE 3");
        verify(writer).write(captor.capture());
        assertThat(captor.getValue().getCompanyIdx()).isEqualTo(0L);
    }

    /** 해시는 저장되는 값으로 계산되므로 재계산이 성립해야 한다. */
    @Test void 무결성_해시가_저장값과_일치한다() {
        loginSuperSelecting(9L);
        logger.log(AuditType.UPDATE, "x");
        verify(writer).write(captor.capture());
        var row = captor.getValue();
        assertThat(row.getIntergrityHash()).isEqualTo(AuditLogger.integrityHash(row));
    }
}
