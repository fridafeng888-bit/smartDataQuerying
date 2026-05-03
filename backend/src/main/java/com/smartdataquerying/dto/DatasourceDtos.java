package com.smartdataquerying.dto;

import com.smartdataquerying.model.DatasourceType;

import java.util.List;

public class DatasourceDtos {
    public record DatasourceRequest(
            String name,
            DatasourceType type,
            String host,
            Integer port,
            String databaseName,
            String username,
            String password,
            boolean sslEnabled,
            boolean enabled
    ) {
    }

    public record DatasourceResponse(
            Long id,
            String name,
            DatasourceType type,
            String host,
            Integer port,
            String databaseName,
            String username,
            boolean sslEnabled,
            boolean enabled
    ) {
    }

    public record TestConnectionResponse(boolean connected, String message) {
    }

    public record ColumnResponse(Long id, String columnName, String dataType, boolean nullableCol,
                                 String commentText, boolean sensitive, boolean enabled, int ordinalPosition) {
    }

    public record TableResponse(Long id, String schemaName, String tableName, String commentText,
                                boolean enabled, List<ColumnResponse> columns) {
    }

    public record TablePatchRequest(String commentText, Boolean enabled) {
    }

    public record ColumnPatchRequest(String commentText, Boolean sensitive, Boolean enabled) {
    }
}

