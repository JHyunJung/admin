package com.crosscert.fidoadmin.dashboard;

import java.util.List;

public record StatTotals(long authS, long authF, long tcS, long tcF, long regS, long regF, long deregS, long deregF) {

    public static StatTotals of(List<DailyStat> rows) {
        long[] s = new long[8];
        for (DailyStat r : rows) {
            s[0] += r.authS(); s[1] += r.authF(); s[2] += r.tcS(); s[3] += r.tcF();
            s[4] += r.regS(); s[5] += r.regF(); s[6] += r.deregS(); s[7] += r.deregF();
        }
        return new StatTotals(s[0], s[1], s[2], s[3], s[4], s[5], s[6], s[7]);
    }

    public long authTotal() { return authS + authF; }

    /** 소수 첫째 자리까지. 분모 0 이면 0. */
    public double authSuccessRate() {
        return authTotal() == 0 ? 0.0 : Math.round(authS * 1000.0 / authTotal()) / 10.0;
    }
}
