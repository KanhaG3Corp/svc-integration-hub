package com.g3cs.integration.common.exception;

import com.g3cs.integration.common.dto.ApiResponseDto;
import com.g3cs.integration.common.message.MessageCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleBusiness(BusinessException ex) {
        String message = ex.getCode().resolve(ex.getArgs());
        log.warn("BusinessException code={} message={}", ex.getCode(), message);
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ApiResponseDto.error(ex.getCode().name(), message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseDto<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().isEmpty()
                ? MessageCode.VALIDATION_FAILED.template()
                : ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponseDto.error(MessageCode.VALIDATION_FAILED.name(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseDto<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponseDto.error(MessageCode.INTERNAL_ERROR.name(), MessageCode.INTERNAL_ERROR.template()));
    }
}
