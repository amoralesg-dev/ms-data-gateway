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
public class GatewayServiceEntity {
    private Long id;
    private String serviceCode;
    private String serviceName;
    private String serviceType;
    private String mapperClass;
    private String operationName;
    private String endpointUrl;
    private boolean enabled;
    private int timeoutSeconds;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;
}
