package com.gateway.service;

import com.gateway.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataService {

    private final DataSource dataSource;

    public List<String> listCatalogs() throws SQLException {
        List<String> catalogs = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getCatalogs()) {
                while (rs.next()) {
                    catalogs.add(rs.getString("TABLE_CAT"));
                }
            }
        }
        return catalogs;
    }

    public List<SchemaInfo> listSchemas() throws SQLException {
        List<SchemaInfo> schemas = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getSchemas()) {
                while (rs.next()) {
                    schemas.add(SchemaInfo.builder()
                            .schemaName(rs.getString("TABLE_SCHEM"))
                            .catalogName(rs.getString("TABLE_CATALOG"))
                            .build());
                }
            }
        }
        return schemas;
    }

    public List<TableInfo> listTables(String schema, String tableNamePattern) throws SQLException {
        List<TableInfo> tables = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String[] types = {"TABLE", "VIEW"};
            try (ResultSet rs = meta.getTables(null, schema, 
                    tableNamePattern != null ? tableNamePattern : "%", types)) {
                while (rs.next()) {
                    tables.add(TableInfo.builder()
                            .catalog(rs.getString("TABLE_CAT"))
                            .schema(rs.getString("TABLE_SCHEM"))
                            .tableName(rs.getString("TABLE_NAME"))
                            .tableType(rs.getString("TABLE_TYPE"))
                            .remarks(rs.getString("REMARKS"))
                            .build());
                }
            }
        }
        return tables;
    }

    public List<ColumnInfo> listColumns(String schema, String tableName) throws SQLException {
        List<ColumnInfo> columns = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getColumns(null, schema, tableName, "%")) {
                while (rs.next()) {
                    columns.add(ColumnInfo.builder()
                            .name(rs.getString("COLUMN_NAME"))
                            .type(rs.getString("TYPE_NAME"))
                            .jdbcType(rs.getInt("DATA_TYPE"))
                            .columnSize(rs.getInt("COLUMN_SIZE"))
                            .nullable(rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls)
                            .label(rs.getString("REMARKS"))
                            .build());
                }
            }
        }
        return columns;
    }

    public List<PrimaryKeyInfo> getPrimaryKeys(String schema, String tableName) throws SQLException {
        List<PrimaryKeyInfo> pks = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getPrimaryKeys(null, schema, tableName)) {
                while (rs.next()) {
                    pks.add(PrimaryKeyInfo.builder()
                            .tableName(rs.getString("TABLE_NAME"))
                            .columnName(rs.getString("COLUMN_NAME"))
                            .keySequence(rs.getInt("KEY_SEQ"))
                            .pkName(rs.getString("PK_NAME"))
                            .build());
                }
            }
        }
        return pks;
    }

    public List<IndexInfo> getIndexes(String schema, String tableName) throws SQLException {
        List<IndexInfo> indexes = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getIndexInfo(null, schema, tableName, false, false)) {
                while (rs.next()) {
                    indexes.add(IndexInfo.builder()
                            .tableName(rs.getString("TABLE_NAME"))
                            .indexName(rs.getString("INDEX_NAME"))
                            .nonUnique(rs.getBoolean("NON_UNIQUE"))
                            .columnName(rs.getString("COLUMN_NAME"))
                            .ordinalPosition(rs.getInt("ORDINAL_POSITION"))
                            .ascOrDesc(rs.getString("ASC_OR_DESC"))
                            .build());
                }
            }
        }
        return indexes;
    }

    public List<ForeignKeyInfo> getForeignKeys(String schema, String tableName) throws SQLException {
        List<ForeignKeyInfo> fks = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getImportedKeys(null, schema, tableName)) {
                while (rs.next()) {
                    fks.add(ForeignKeyInfo.builder()
                            .fkTableName(rs.getString("FKTABLE_NAME"))
                            .fkColumnName(rs.getString("FKCOLUMN_NAME"))
                            .pkTableName(rs.getString("PKTABLE_NAME"))
                            .pkColumnName(rs.getString("PKCOLUMN_NAME"))
                            .fkName(rs.getString("FK_NAME"))
                            .pkName(rs.getString("PK_NAME"))
                            .updateRule(rs.getInt("UPDATE_RULE"))
                            .deleteRule(rs.getInt("DELETE_RULE"))
                            .build());
                }
            }
        }
        return fks;
    }

    public List<String> getJdbcTypeNames() {
        List<String> types = new ArrayList<>();
        java.lang.reflect.Field[] fields = java.sql.Types.class.getFields();
        for (java.lang.reflect.Field f : fields) {
            types.add(f.getName());
        }
        return types;
    }

    public String getDatabaseInfo() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            return String.format("{\"product\":\"%s\",\"version\":\"%s\",\"driver\":\"%s\",\"driverVersion\":\"%s\"}",
                    meta.getDatabaseProductName(),
                    meta.getDatabaseProductVersion(),
                    meta.getDriverName(),
                    meta.getDriverVersion());
        }
    }
}
