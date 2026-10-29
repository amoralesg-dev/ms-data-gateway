package com.gateway.catalog;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayQueryParameterEntity {
    private Long id;
    private String queryCode;
    private String parameterName;
    private String parameterType;
    private boolean requiredFlag;
    private String defaultValue;
}
