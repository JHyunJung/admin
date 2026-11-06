package com.crosscert.fidoadmin.common

/**
 * 사이드바 메뉴 한 줄. `icon` 은 Bootstrap Icons 클래스명(예: `bi-people`)으로,
 * 템플릿이 `<i class="bi ..."></i>` 에 그대로 넣는다.
 *
 * `area` 는 테넌트 선택이 필요한지를, `superOnly` 는 역할을 가린다.
 * 둘은 직교한다(라이선스 = TENANT + superOnly).
 *
 * `hidden` 은 "화면은 살아 있지만 사이드바에 내놓지 않는다" 는 뜻이다. 운영 동선에서
 * 치운 화면이라도 항목 자체를 지우면 [MenuRegistry.areaOf] 가 그 경로를 모르게
 * 되어 SYSTEM 으로 판정한다. TENANT 화면이 그렇게 되면 테넌트 선택 인터셉터가 막지 못하고,
 * 대신 서비스 계층이 `NoTenantSelectedException` 을 던져 GlobalExceptionHandler 가
 * "인터셉터가 놓친 경로" 경고를 남긴다 — 화면은 똑같이 동작하지만 운영 로그가 더러워진다.
 * 그래서 감출 때는 지우지 말고 이 플래그를 쓴다.
 *
 * Java record 였으므로 같은 접근자(`area()`, `href()` …)를 내도록 [JvmRecord] 로 둔다.
 */
@JvmRecord
data class MenuItem(
    val area: MenuArea, val group: String, val title: String, val href: String,
    val superOnly: Boolean, val icon: String, val hidden: Boolean,
) {

    /** 사이드바에 보이는 보통의 메뉴. */
    constructor(area: MenuArea, group: String, title: String, href: String, superOnly: Boolean, icon: String) :
        this(area, group, title, href, superOnly, icon, false)
}
