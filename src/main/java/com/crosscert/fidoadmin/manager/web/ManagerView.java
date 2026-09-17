package com.crosscert.fidoadmin.manager.web;

import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import java.time.LocalDateTime;

/** 상세. ERD 16개 컬럼 중 USER_PW 만 제외한다. */
public record ManagerView(Long idx, String userId, String userNm, String userEmail, String userPhone, Long companyIdx,
                          String status, String login, LocalDateTime blockTime, LocalDateTime lastAccess, String etc,
                          String alramType, String alramLevel, LocalDateTime createdtime, LocalDateTime updatedtime) {
    public static ManagerView of(CcfaManager m) {
        return new ManagerView(m.getIdx(), m.getUserId(), m.getUserNm(), m.getUserEmail(), m.getUserPhone(), m.getCompanyIdx(),
            m.getStatus(), m.getLogin(), m.getBlockTime(), m.getLastAccess(), m.getEtc(),
            m.getAlramType(), m.getAlramLevel(), m.getCreatedtime(), m.getUpdatedtime());
    }
}
