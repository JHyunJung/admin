package com.crosscert.fidoadmin.common

/** 다른 고객사의 데이터에 접근했을 때. 존재 자체를 숨기기 위해 404 로 처리한다. */
open class TenantMismatchException(message: String?) : RuntimeException(message)
