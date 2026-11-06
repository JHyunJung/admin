package com.crosscert.fidoadmin.common

import jakarta.persistence.EntityNotFoundException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.resource.NoResourceFoundException

@ControllerAdvice
class GlobalExceptionHandler {

    // 경로 변수를 대상 타입으로 바꿀 수 없으면(@PathVariable Long "abc", CcfaSystemPropId "NO_AT" 등)
    // 그 값은 어떤 자원도 가리키지 않으므로 404 로 처리한다("존재를 숨긴다" 정책과 같다).
    @ExceptionHandler(EntityNotFoundException::class, TenantMismatchException::class, NoResourceFoundException::class,
                      MethodArgumentTypeMismatchException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound(e: Exception): String {
        log.debug("404: {}", e.message)
        return "error/404"
    }

    @ExceptionHandler(AccessDeniedException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun forbidden(e: AccessDeniedException): String = "error/403"

    /**
     * 인터셉터가 놓친 경로에서 유효 테넌트를 요구했다. 500 대신 선택 화면으로 보낸다.
     *
     * 여기 걸리는 경로가 있다면 MenuRegistry 에 등록되지 않은 테넌트 화면이라는
     * 뜻이므로 로그를 남긴다.
     */
    @ExceptionHandler(NoTenantSelectedException::class)
    fun noTenant(e: NoTenantSelectedException, request: HttpServletRequest): String {
        log.warn("테넌트 미선택 상태로 테넌트 데이터를 요청했다. 인터셉터가 놓친 경로일 수 있다: {}",
            request.requestURI)
        return "redirect:/select-tenant"
    }

    /**
     * 서비스가 상태 코드를 정해 던진 예외(FIDO 로그 404, 잘못된 조회 날짜 400 등)는 그 코드대로 응답한다.
     * 아래 catch-all 에 걸리면 정상적인 404 가 500 으로 둔갑하고 오류 로그까지 남는다.
     */
    @ExceptionHandler(ResponseStatusException::class)
    fun responseStatus(e: ResponseStatusException): ModelAndView {
        val status = HttpStatus.resolve(e.statusCode.value()) ?: HttpStatus.INTERNAL_SERVER_ERROR
        val view = when (status) {
            HttpStatus.NOT_FOUND -> "error/404"
            HttpStatus.FORBIDDEN -> "error/403"
            else -> "error/500"
        }
        if (status.is5xxServerError) log.error("처리되지 않은 예외", e)
        else log.debug("{}: {}", status.value(), e.reason)
        return ModelAndView(view, status)
    }

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun internal(e: Exception): String {
        log.error("처리되지 않은 예외", e)
        return "error/500"
    }

    private companion object {
        private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }
}
