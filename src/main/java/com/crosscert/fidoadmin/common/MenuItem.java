package com.crosscert.fidoadmin.common;

/**
 * 사이드바 메뉴 한 줄. {@code icon} 은 Bootstrap Icons 클래스명(예: {@code bi-people})으로,
 * 템플릿이 {@code <i class="bi ..."></i>} 에 그대로 넣는다.
 */
public record MenuItem(String group, String title, String href, boolean superOnly, String icon) {}
