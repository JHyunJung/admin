package com.crosscert.fidoadmin.common;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    // 경로 변수를 대상 타입으로 바꿀 수 없으면(@PathVariable Long "abc", CcfaSystemPropId "NO_AT" 등)
    // 그 값은 어떤 자원도 가리키지 않으므로 404 로 처리한다("존재를 숨긴다" 정책과 같다).
    @ExceptionHandler({EntityNotFoundException.class, TenantMismatchException.class, NoResourceFoundException.class,
                        MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(Exception e) {
        log.debug("404: {}", e.getMessage());
        return "error/404";
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String forbidden(AccessDeniedException e) {
        return "error/403";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String internal(Exception e) {
        log.error("처리되지 않은 예외", e);
        return "error/500";
    }
}
