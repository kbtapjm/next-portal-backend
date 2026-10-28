package com.nextportal.api.error;

import com.nextportal.api.error.dto.FieldErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrors {

    @ExceptionHandler({
        PortalException.class,
        ConstraintViolationException.class,
        DataIntegrityViolationException.class,
        OptimisticLockingFailureException.class
    })
    ProblemDetail business(RuntimeException exception) {
        return switch (exception) {
            case PortalException error -> ProblemDetail.forStatusAndDetail(error.status(), error.getMessage());
            case ConstraintViolationException error -> ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "입력값을 확인해주세요.");
            case DataIntegrityViolationException error -> ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "중복 데이터가 발생했습니다.");
            case OptimisticLockingFailureException error -> ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "동시 수정이 발생했습니다.");
            default -> throw new IllegalStateException("Unexpected exception handler input", exception);
        };
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException error) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "입력값을 확인해주세요.");
        detail.setProperty("errors", error.getBindingResult().getFieldErrors().stream()
            .map(field -> new FieldErrorResponse(field.getField(), field.getDefaultMessage()))
            .toList());

        return detail;
    }
}
