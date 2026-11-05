package com.crosscert.fidoadmin.statistics.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.crosscert.fidoadmin.statistics.batch.LogClassifier.Kind;
import com.crosscert.fidoadmin.statistics.batch.LogClassifier.Result;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/** 구 배치(FIDOStatistics.push)의 규칙표. */
class LogClassifierTest {

    private static String enc(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String tx(String op, String status) {
        return tx(op, status, null);
    }

    private static String tx(String op, String status, String request) {
        StringBuilder b = new StringBuilder("{\"transaction\":{\"serviceName\":\"svc\"");
        if (op != null) b.append(",\"op\":\"").append(op).append('"');
        if (status != null) b.append(",\"status\":\"").append(status).append('"');
        if (request != null) b.append(",\"request\":").append(request);
        return b.append("}}").toString();
    }

    private static Result of(String json) {
        return LogClassifier.classify(enc(json)).orElseThrow();
    }

    @Test void TC는_TC다() {
        assertThat(of(tx("TC", "Success"))).isEqualTo(new Result(Kind.TC, true));
    }

    @Test void Auth_요청데이터에_transaction이_있으면_TC다() {
        String req = "{\"requestData\":\"{\\\"transaction\\\":[{\\\"content\\\":\\\"x\\\"}]}\"}";
        assertThat(of(tx("Auth", "Success", req))).isEqualTo(new Result(Kind.TC, true));
    }

    @Test void Auth_요청데이터가_객체여도_문자열로_찾는다() {
        assertThat(of(tx("Auth", "Error", "{\"requestData\":{\"transaction\":[]}}"))).isEqualTo(new Result(Kind.TC, false));
    }

    @Test void Auth_요청데이터에_transaction이_없으면_Auth다() {
        assertThat(of(tx("Auth", "Success", "{\"requestData\":\"{\\\"policy\\\":1}\"}"))).isEqualTo(new Result(Kind.AUTH, true));
        assertThat(of(tx("Auth", "Success"))).isEqualTo(new Result(Kind.AUTH, true));
    }

    @Test void Reg와_DeReg() {
        assertThat(of(tx("Reg", "Success"))).isEqualTo(new Result(Kind.REG, true));
        assertThat(of(tx("DeReg", "Wait"))).isEqualTo(new Result(Kind.DEREG, false));
    }

    @Test void op는_대소문자가_정확히_같아야_한다() {
        assertThat(of(tx("auth", "Success")).kind()).isEqualTo(Kind.NONE);
        assertThat(of(tx("DEREG", "Success")).kind()).isEqualTo(Kind.NONE);
        assertThat(of(tx("tc", "Success")).kind()).isEqualTo(Kind.NONE);
    }

    @Test void 모르는_op나_op없음은_세지_않는다() {
        assertThat(of(tx("Unknown", "Success")).kind()).isEqualTo(Kind.NONE);
        assertThat(of(tx(null, "Success")).kind()).isEqualTo(Kind.NONE);
    }

    @Test void Success만_성공이다() {
        assertThat(of(tx("Reg", "Success")).success()).isTrue();
        assertThat(of(tx("Reg", "success")).success()).isFalse();
        assertThat(of(tx("Reg", "RequestOK")).success()).isFalse();
        assertThat(of(tx("Reg", "ResponseOK")).success()).isFalse();
        assertThat(of(tx("Reg", "Error")).success()).isFalse();
        assertThat(of(tx("Reg", null)).success()).isFalse();
    }

    @Test void 평문_JSON도_받는다() {
        assertThat(LogClassifier.classify(tx("Reg", "Success"))).contains(new Result(Kind.REG, true));
    }

    @Test void 깨진_로그는_비어_있다() {
        assertThat(LogClassifier.classify("@@not-base64@@")).isEmpty();
        assertThat(LogClassifier.classify(enc("not json"))).isEmpty();
        assertThat(LogClassifier.classify(null)).isEmpty();
        assertThat(LogClassifier.classify("")).isEmpty();
    }

    @Test void StatCounts는_칸별로_더한다() {
        StatCounts c = new StatCounts()
            .add(Kind.AUTH, true).add(Kind.AUTH, false).add(Kind.TC, true).add(Kind.REG, false)
            .add(Kind.DEREG, true).add(Kind.NONE, true);
        assertThat(c.getAuthS()).isEqualTo(1);
        assertThat(c.getAuthF()).isEqualTo(1);
        assertThat(c.getTcS()).isEqualTo(1);
        assertThat(c.getRegF()).isEqualTo(1);
        assertThat(c.getDeregS()).isEqualTo(1);
        assertThat(c.getTcF() + c.getRegS() + c.getDeregF()).isZero();
    }
}
