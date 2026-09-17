package com.crosscert.fidoadmin.signup;

import static org.assertj.core.api.Assertions.assertThat;

import com.crosscert.fidoadmin.common.ManagerStatus;
import org.junit.jupiter.api.Test;

class SignupPolicyTest {

    /** 가입 전용 상태는 활성과 겹치지 않아야 한다. 겹치면 미승인 계정이 로그인된다. */
    @Test void pendingAndRejectedAreNotLoginable() {
        assertThat(SignupPolicy.STATUS_PENDING).isNotEqualTo(ManagerStatus.ACTIVE);
        assertThat(SignupPolicy.STATUS_REJECTED).isNotEqualTo(ManagerStatus.ACTIVE);
        assertThat(SignupPolicy.STATUS_PENDING).isNotEqualTo(SignupPolicy.STATUS_REJECTED);
    }

    /** 미배정 표식은 실제 고객사 IDX(0 이상)와 겹치지 않아야 한다. 0 은 SUPER 다. */
    @Test void unassignedCompanyIdxNeverCollidesWithRealCompany() {
        assertThat(SignupPolicy.UNASSIGNED_COMPANY_IDX).isNegative();
        assertThat(SignupPolicy.UNASSIGNED_COMPANY_IDX).isNotEqualTo(SignupPolicy.SUPER_COMPANY_IDX);
    }
}
