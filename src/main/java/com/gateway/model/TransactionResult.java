package com.gateway.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionResult {
    private boolean success;
    private List<WriteResult> operationResults;
    private Integer failedOperationIndex;
    private String failedOperationId;
    private String errorCode;
    private String message;
    private Long totalExecutionTimeMs;
}
