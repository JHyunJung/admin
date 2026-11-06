package com.crosscert.fidoadmin.statistics.batch

import com.crosscert.fidoadmin.statistics.batch.LogClassifier.Kind
import com.crosscert.fidoadmin.statistics.batch.LogClassifier.Result
import java.nio.charset.StandardCharsets
import java.util.Base64
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** 구 배치(FIDOStatistics.push)의 규칙표. */
class LogClassifierTest {

    companion object {
        private fun enc(json: String): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray(StandardCharsets.UTF_8))

        private fun tx(op: String?, status: String?): String = tx(op, status, null)

        private fun tx(op: String?, status: String?, request: String?): String {
            val b = StringBuilder("{\"transaction\":{\"serviceName\":\"svc\"")
            if (op != null) b.append(",\"op\":\"").append(op).append('"')
            if (status != null) b.append(",\"status\":\"").append(status).append('"')
            if (request != null) b.append(",\"request\":").append(request)
            return b.append("}}").toString()
        }

        private fun of(json: String): Result = LogClassifier.classify(enc(json)).orElseThrow()
    }

    @Test fun TC는_TC다() {
        assertThat(of(tx("TC", "Success"))).isEqualTo(Result(Kind.TC, true))
    }

    @Test fun Auth_요청데이터에_transaction이_있으면_TC다() {
        val req = "{\"requestData\":\"{\\\"transaction\\\":[{\\\"content\\\":\\\"x\\\"}]}\"}"
        assertThat(of(tx("Auth", "Success", req))).isEqualTo(Result(Kind.TC, true))
    }

    @Test fun Auth_요청데이터가_객체여도_문자열로_찾는다() {
        assertThat(of(tx("Auth", "Error", "{\"requestData\":{\"transaction\":[]}}"))).isEqualTo(Result(Kind.TC, false))
    }

    @Test fun Auth_요청데이터에_transaction이_없으면_Auth다() {
        assertThat(of(tx("Auth", "Success", "{\"requestData\":\"{\\\"policy\\\":1}\"}"))).isEqualTo(Result(Kind.AUTH, true))
        assertThat(of(tx("Auth", "Success"))).isEqualTo(Result(Kind.AUTH, true))
    }

    @Test fun Reg와_DeReg() {
        assertThat(of(tx("Reg", "Success"))).isEqualTo(Result(Kind.REG, true))
        assertThat(of(tx("DeReg", "Wait"))).isEqualTo(Result(Kind.DEREG, false))
    }

    @Test fun op는_대소문자가_정확히_같아야_한다() {
        assertThat(of(tx("auth", "Success")).kind).isEqualTo(Kind.NONE)
        assertThat(of(tx("DEREG", "Success")).kind).isEqualTo(Kind.NONE)
        assertThat(of(tx("tc", "Success")).kind).isEqualTo(Kind.NONE)
    }

    @Test fun 모르는_op나_op없음은_세지_않는다() {
        assertThat(of(tx("Unknown", "Success")).kind).isEqualTo(Kind.NONE)
        assertThat(of(tx(null, "Success")).kind).isEqualTo(Kind.NONE)
    }

    @Test fun Success만_성공이다() {
        assertThat(of(tx("Reg", "Success")).success).isTrue()
        assertThat(of(tx("Reg", "success")).success).isFalse()
        assertThat(of(tx("Reg", "RequestOK")).success).isFalse()
        assertThat(of(tx("Reg", "ResponseOK")).success).isFalse()
        assertThat(of(tx("Reg", "Error")).success).isFalse()
        assertThat(of(tx("Reg", null)).success).isFalse()
    }

    @Test fun 평문_JSON도_받는다() {
        assertThat(LogClassifier.classify(tx("Reg", "Success"))).contains(Result(Kind.REG, true))
    }

    @Test fun 깨진_로그는_비어_있다() {
        assertThat(LogClassifier.classify("@@not-base64@@")).isEmpty()
        assertThat(LogClassifier.classify(enc("not json"))).isEmpty()
        assertThat(LogClassifier.classify(null)).isEmpty()
        assertThat(LogClassifier.classify("")).isEmpty()
    }

    @Test fun StatCounts는_칸별로_더한다() {
        val c = StatCounts()
            .add(Kind.AUTH, true).add(Kind.AUTH, false).add(Kind.TC, true).add(Kind.REG, false)
            .add(Kind.DEREG, true).add(Kind.NONE, true)
        assertThat(c.authS).isEqualTo(1)
        assertThat(c.authF).isEqualTo(1)
        assertThat(c.tcS).isEqualTo(1)
        assertThat(c.regF).isEqualTo(1)
        assertThat(c.deregS).isEqualTo(1)
        assertThat(c.tcF + c.regS + c.deregF).isZero()
    }
}
