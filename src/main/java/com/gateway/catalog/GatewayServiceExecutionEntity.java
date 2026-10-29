package com.gateway.catalog;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayServiceExecutionEntity {
    private Long id;
    private String serviceCode;
    private String requestPayload;
    private String responsePayload;
    private String status; // SUCCESS, ERROR, FAULT
    private String errorCode;
    private String errorMessage;
    private long executionTimeMs;
    private String executedBy;
    private String sourceIp;
    private LocalDateTime createdAt;
}
