package com.smartdataquerying.dto;

import java.time.Instant;
import java.util.List;

public class ExcelDtos {
    public record ExcelImportResponse(
            Long id,
            Long datasourceId,
            Long tableId,
            String originalFileName,
            String physicalTableName,
            String displayTableName,
            int rowCount,
            int columnCount,
            String status,
            String errorMessage,
            Instant createdAt,
            List<ImportedColumn> columns
    ) {
    }

    public record ImportedColumn(String columnName, String originalHeader, String dataType) {
    }
}

