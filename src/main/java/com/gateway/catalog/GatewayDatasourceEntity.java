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
public class GatewayDatasourceEntity {
    private String datasourceId;
    private String datasourceCode;
    private String datasourceName;
    private String engineType;
    private boolean enabled;
    private boolean defaultFlag;
    private String description;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
}
