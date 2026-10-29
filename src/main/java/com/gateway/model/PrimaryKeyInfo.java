package com.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrimaryKeyInfo {
    private String tableName;
    private String columnName;
    private int keySequence;
    private String pkName;
}
