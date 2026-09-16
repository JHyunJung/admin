package com.crosscert.fidoadmin.dashboard;

import java.time.LocalDate;

public record DailyStat(LocalDate date, long authS, long authF, long tcS, long tcF,
                        long regS, long regF, long deregS, long deregF) {}
