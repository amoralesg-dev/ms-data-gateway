package com.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexInfo {
    private String tableName;
    private String indexName;
    private boolean nonUnique;
    private String columnName;
    private int ordinalPosition;
    private String ascOrDesc;
}
