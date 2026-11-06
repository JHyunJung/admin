package com.crosscert.fidoadmin.statistics.batch

/** 한 (시간, 회사, 서비스) 의 여덟 칸 누적값. 집계 중에만 쓰는 가변 객체다. */
class StatCounts {
    var authS: Long = 0; private set
    var authF: Long = 0; private set
    var tcS: Long = 0; private set
    var tcF: Long = 0; private set
    var regS: Long = 0; private set
    var regF: Long = 0; private set
    var deregS: Long = 0; private set
    var deregF: Long = 0; private set

    fun add(kind: LogClassifier.Kind, success: Boolean): StatCounts {
        when (kind) {
            LogClassifier.Kind.AUTH -> if (success) authS++ else authF++
            LogClassifier.Kind.TC -> if (success) tcS++ else tcF++
            LogClassifier.Kind.REG -> if (success) regS++ else regF++
            LogClassifier.Kind.DEREG -> if (success) deregS++ else deregF++
            LogClassifier.Kind.NONE -> {}
        }
        return this
    }
}
