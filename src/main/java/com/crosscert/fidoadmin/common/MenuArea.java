package com.crosscert.fidoadmin.common;

/**
 * 화면이 속한 영역.
 *
 * <p>기존 {@code superOnly} 는 "SUPER 만 보는 화면"과 "COMPANY_IDX 가 없는 전역
 * 테이블"을 뭉뚱그렸다. 테넌트 선택 모델에서는 이 둘이 갈라진다 — 라이선스·운영자는
 * SUPER 전용이면서 테넌트 데이터다. 그래서 영역은 superOnly 와 직교한다.
 */
public enum MenuArea {
    /** 테넌트 데이터. 고객사를 선택해야 열린다. */
    TENANT,
    /** 전역 테이블과 테넌트에 속하지 않는 워크플로. 선택과 무관하다. */
    SYSTEM,
    /** 로그인한 본인에 대한 화면. 선택과 무관하다. */
    PERSONAL
}
