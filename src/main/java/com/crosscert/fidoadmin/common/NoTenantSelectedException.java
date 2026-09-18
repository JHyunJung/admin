package com.crosscert.fidoadmin.common;

/**
 * 슈퍼관리자가 고객사를 고르지 않은 채 테넌트 화면의 데이터를 요청했다.
 *
 * <p>미선택을 null 로 표현하지 않는 이유: {@code Specs.eq(attr, null)} 은 술어를
 * 생략하므로, null 이 흘러가면 필터가 조용히 사라져 전체 조회가 된다.
 * 예외로 두면 누락이 선택 화면 리다이렉트로 드러난다.
 */
public class NoTenantSelectedException extends RuntimeException {
    public NoTenantSelectedException() {
        super("고객사가 선택되지 않았습니다");
    }
}
