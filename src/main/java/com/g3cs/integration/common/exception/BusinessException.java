package com.g3cs.integration.common.exception;

import com.g3cs.integration.common.message.MessageCode;
import lombok.Getter;

import java.util.Map;

@Getter
public class BusinessException extends RuntimeException {

    private final MessageCode code;
    private final Map<String, Object> args;
    private final int httpStatus;

    public BusinessException(MessageCode code) {
        this(code, Map.of(), 400);
    }

    public BusinessException(MessageCode code, Map<String, Object> args) {
        this(code, args, 400);
    }

    public BusinessException(MessageCode code, Map<String, Object> args, int httpStatus) {
        super(code.name());
        this.code = code;
        this.args = args == null ? Map.of() : args;
        this.httpStatus = httpStatus;
    }
}
