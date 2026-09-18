package com.crosscert.fidoadmin.common;

/**
 * 사이드바 메뉴 한 줄. {@code icon} 은 Bootstrap Icons 클래스명(예: {@code bi-people})으로,
 * 템플릿이 {@code <i class="bi ..."></i>} 에 그대로 넣는다.
 *
 * <p>{@code area} 는 테넌트 선택이 필요한지를, {@code superOnly} 는 역할을 가린다.
 * 둘은 직교한다(라이선스 = TENANT + superOnly).
 */
public record MenuItem(MenuArea area, String group, String title, String href,
                       boolean superOnly, String icon) {}
