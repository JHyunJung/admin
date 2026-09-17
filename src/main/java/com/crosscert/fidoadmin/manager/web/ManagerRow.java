package com.crosscert.fidoadmin.manager.web;

import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import java.time.LocalDateTime;

/** 목록 행. USER_PW 제외. */
public record ManagerRow(Long idx, String userId, String userNm, String userEmail, Long companyIdx,
                         String status, String login, LocalDateTime lastAccess) {
    public static ManagerRow of(CcfaManager m) {
        return new ManagerRow(m.getIdx(), m.getUserId(), m.getUserNm(), m.getUserEmail(), m.getCompanyIdx(),
            m.getStatus(), m.getLogin(), m.getLastAccess());
    }
}
