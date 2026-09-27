package com.crosscert.fidoadmin.log.service;

/**
 * FIDO 로그 JSONDATA 에서 화면이 꺼내 쓰는 값들의 위치와 해석.
 *
 * <p>이전 어드민은 JSONDATA 를 파싱하지 않고 원문만 보여 줬고, ERD 에도 JSON 구조 설명이
 * 없다. 그래서 키 이름은 이 프로젝트의 시드·더미가 쓰는 규약
 * ({@code {"op":"Auth|Reg|Dereg","userid":"…","result":"1200"}})을 따른다.
 * <b>실제 FIDO 서버의 키가 다르면 여기 경로만 바꾸면 된다</b> — SQL 과 화면은 이 상수만 본다.
 *
 * <p>값은 DB 에서 {@code JSON_VALUE} 로 꺼낸다(Oracle 12.1+). CLOB 을 실어 오지 않고,
 * JSON 이 아니거나 키가 없으면 NULL 이 된다(기본 NULL ON ERROR).
 */
public final class FidoLogJson {

    /** 등록/인증/해지 구분. */
    public static final String OP_PATH = "$.op";
    /** 사용자 ID. */
    public static final String USERID_PATH = "$.userid";
    /** 결과 코드(UAF 상태 코드, 1200 = 성공). */
    public static final String RESULT_PATH = "$.result";
    public static final String SUCCESS_CODE = "1200";

    public static final String OP_REG = "Reg";
    public static final String OP_AUTH = "Auth";
    public static final String OP_DEREG = "Dereg";

    public static final String OUTCOME_SUCCESS = "success";
    public static final String OUTCOME_FAIL = "fail";

    private FidoLogJson() {}

    /** SQL 조각. 컬럼 이름과 경로는 위 상수(고정 문자열)만 들어간다 — 사용자 입력은 싣지 않는다. */
    static String jsonValue(String column, String path) {
        return "JSON_VALUE(" + column + ", '" + path + "')";
    }

    /** 구분 표시어. 모르는 값은 원문 그대로, 없으면 null. */
    public static String opLabel(String op) {
        if (op == null || op.isBlank()) return null;
        return switch (op) {
            case OP_REG -> "등록";
            case OP_AUTH -> "인증";
            case OP_DEREG -> "해지";
            default -> op;
        };
    }

    public static boolean isSuccess(String result) {
        return SUCCESS_CODE.equals(result);
    }

    /** 검색 폼의 결과 필터. success/fail 만 받고 그 밖(빈 값 포함)은 조건 없음(null). */
    public static String outcomeOrNull(String outcome) {
        if (OUTCOME_SUCCESS.equals(outcome) || OUTCOME_FAIL.equals(outcome)) return outcome;
        return null;
    }

    /** 검색 폼의 구분 필터. 아는 값만 받고 그 밖은 조건 없음(null). */
    public static String opOrNull(String op) {
        if (OP_REG.equals(op) || OP_AUTH.equals(op) || OP_DEREG.equals(op)) return op;
        return null;
    }
}
