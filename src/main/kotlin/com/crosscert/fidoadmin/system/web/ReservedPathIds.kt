package com.crosscert.fidoadmin.system.web

import org.springframework.validation.BindingResult

/** CRUD 경로의 {id} 세그먼트로 그대로 쓰이는 식별자(코드·키)의 문자 집합. 폼의 @Pattern 이 쓴다. */
internal const val PATH_ID_PATTERN = "[A-Za-z0-9._-]+"

private val DOTS_ONLY = Regex("\\.+")

/**
 * 등록 시 경로 {id} 로 쓸 수 없는 예약값을 막는다. "new" 는 등록 폼 경로("/new")와 겹치고,
 * 점으로만 된 값은 경로 세그먼트로서 특수한 의미를 가져 상세/수정/삭제 링크가 만들어지지 않는다.
 * [extraReserved] 는 컨트롤러가 따로 쓰는 리터럴 경로(예: "reload")다. 비교는 대소문자를 무시한다.
 */
internal fun rejectReservedPathId(
    value: String?,
    isNew: Boolean,
    binding: BindingResult,
    field: String,
    noun: String,
    vararg extraReserved: String,
) {
    if (isNew && value != null && isReservedPathId(value, *extraReserved)) {
        binding.rejectValue(field, "reserved", noun + "로 쓸 수 없는 값입니다: " + value)
    }
}

/** [rejectReservedPathId] 의 판정만. 폼이 아닌 곳(FIDO 서버 자가등록)도 같은 규칙을 쓴다. */
internal fun isReservedPathId(value: String, vararg extraReserved: String): Boolean =
    value.equals("new", ignoreCase = true) || extraReserved.any { value.equals(it, ignoreCase = true) } || value.matches(DOTS_ONLY)
