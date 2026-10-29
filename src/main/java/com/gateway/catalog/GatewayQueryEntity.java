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
public class GatewayQueryEntity {
    private String queryCode;
    private String queryName;
    private String datasourceId;
    private String queryType;
    private String sqlText;
    private boolean enabled;
    private int timeoutSeconds;
    private int maxRows;
    private String description;
    private String createdBy;
    private LocalDateTime createdDate;
    private String updatedBy;
    private LocalDateTime updatedDate;
}
