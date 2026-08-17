package com.g3cs.integration.spi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionTestResult {
    private boolean success;
    private String version;
    private String errorCode;
    private String errorMessage;
}
