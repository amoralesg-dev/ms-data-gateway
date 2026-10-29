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
public class GatewayQueryExecutionEntity {
    private Long executionId;
    private String queryCode;
    private String datasourceId;
    private String executedBy;
    private LocalDateTime executionDate;
    private long executionTimeMs;
    private int rowsReturned;
    private boolean success;
    private String errorMessage;
    private String sourceIp;
}
