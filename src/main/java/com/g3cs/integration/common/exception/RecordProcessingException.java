package com.g3cs.integration.common.exception;

import com.g3cs.integration.common.message.MessageCode;
import lombok.Getter;

import java.util.Map;

@Getter
public class RecordProcessingException extends RuntimeException {
    private final MessageCode code;
    private final Map<String, Object> args;

    public RecordProcessingException(MessageCode code) {
        this(code, Map.of());
    }

    public RecordProcessingException(MessageCode code, Map<String, Object> args) {
        super(code.name());
        this.code = code;
        this.args = args == null ? Map.of() : args;
    }
}
