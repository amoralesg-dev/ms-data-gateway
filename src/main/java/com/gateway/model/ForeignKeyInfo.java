package com.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForeignKeyInfo {
    private String fkTableName;
    private String fkColumnName;
    private String pkTableName;
    private String pkColumnName;
    private String fkName;
    private String pkName;
    private int updateRule;
    private int deleteRule;
}
