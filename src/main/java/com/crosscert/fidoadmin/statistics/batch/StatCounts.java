package com.crosscert.fidoadmin.statistics.batch;

import lombok.Getter;

/** 한 (시간, 회사, 서비스) 의 여덟 칸 누적값. 집계 중에만 쓰는 가변 객체다. */
@Getter
public class StatCounts {
    private long authS, authF, tcS, tcF, regS, regF, deregS, deregF;

    public StatCounts add(LogClassifier.Kind kind, boolean success) {
        switch (kind) {
            case AUTH -> { if (success) authS++; else authF++; }
            case TC -> { if (success) tcS++; else tcF++; }
            case REG -> { if (success) regS++; else regF++; }
            case DEREG -> { if (success) deregS++; else deregF++; }
            case NONE -> { }
        }
        return this;
    }
}
