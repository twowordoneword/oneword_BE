package com.example.musing_BE.common.exception;

import com.example.musing_BE.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.time.format.DateTimeParseException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 비즈니스 예외 (정의된 ErrorCode) */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode ec = e.getErrorCode();
        return ResponseEntity.status(ec.getStatus())
                .body(ApiResponse.fail(ec.getCode(), ec.getMessage()));
    }

    /** 바디 검증 실패 (@Valid) */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse(ErrorCode.VALIDATION_ERROR.getMessage());
        return badRequest(msg);
    }

    /**
     * 잘못된 요청 값 → 400.
     * - DateTimeParseException: month=YYYY-MM 형식 오류
     * - HttpMessageNotReadableException: JSON/enum(mood·weather) 파싱 실패
     * - MethodArgumentTypeMismatchException: 경로변수 날짜 형식 오류
     * - MissingServletRequestParameterException: 필수 파라미터(month) 누락
     * - IllegalArgumentException: enum 변환 실패 등
     */
    @ExceptionHandler({
            DateTimeParseException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e) {
        return badRequest(ErrorCode.VALIDATION_ERROR.getMessage());
    }

    /**
     * DB 제약 위반(동시 저장 레이스 등) → 409.
     *
     * <p>어긴 제약이 무엇인지 보고 코드를 고른다. 예전에는 모든 위반을 일기 중복으로 응답해서,
     * 예컨대 로그인 동시 요청이 사용자 유니크 제약에 걸렸을 때도
     * "이미 해당 날짜의 일기가 있습니다"라는 엉뚱한 메시지가 나갔다.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(DataIntegrityViolationException e) {
        log.warn("Data integrity violation: {}", e.getMessage());
        ErrorCode ec = violates(e, "uq_diaries_user_date")
                ? ErrorCode.DIARY_ALREADY_EXISTS
                : ErrorCode.DATA_CONFLICT;
        return ResponseEntity.status(ec.getStatus())
                .body(ApiResponse.fail(ec.getCode(), ec.getMessage()));
    }

    /** 제약 이름은 드라이버 예외 메시지에만 들어 있어 원인 체인을 따라 내려가며 찾는다. */
    private boolean violates(Throwable e, String constraintName) {
        String needle = constraintName.toLowerCase();
        Throwable cause = e;
        for (int depth = 0; cause != null && depth < 10; depth++) {
            String message = cause.getMessage();
            if (message != null && message.toLowerCase().contains(needle)) {
                return true;
            }
            cause = cause.getCause() == cause ? null : cause.getCause();
        }
        return false;
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuth(AuthenticationException e) {
        ErrorCode ec = ErrorCode.UNAUTHORIZED;
        return ResponseEntity.status(ec.getStatus()).body(ApiResponse.fail(ec.getCode(), ec.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(AccessDeniedException e) {
        ErrorCode ec = ErrorCode.FORBIDDEN;
        return ResponseEntity.status(ec.getStatus()).body(ApiResponse.fail(ec.getCode(), ec.getMessage()));
    }

    /** 존재하지 않는 경로 → 404 (catch-all이 500으로 만들지 않도록 명시 처리) */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."));
    }

    /** 그 외 예상치 못한 예외 → 500 (응답 포맷 통일 + 로깅) */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR.getCode(),
                        ErrorCode.INTERNAL_ERROR.getMessage()));
    }

    private ResponseEntity<ApiResponse<Void>> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ErrorCode.VALIDATION_ERROR.getCode(), message));
    }
}
