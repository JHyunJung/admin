package com.crosscert.fidoadmin.statistics.batch;

/** FIDO_STATISTICS 한 행의 묶음 기준(시간은 바깥 맵이 갖는다). */
public record StatKey(long companyIdx, String serviceName) {}
