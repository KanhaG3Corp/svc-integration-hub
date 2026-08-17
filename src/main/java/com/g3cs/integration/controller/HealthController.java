package com.g3cs.integration.controller;

import com.g3cs.integration.common.dto.ApiResponseDto;
import com.g3cs.integration.common.message.MessageCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/integration-hub")
public class HealthController {

    @GetMapping("/health")
    public ApiResponseDto<Map<String, String>> health() {
        return ApiResponseDto.success(MessageCode.SUCCESS.template(), Map.of("status", "UP"));
    }
}
