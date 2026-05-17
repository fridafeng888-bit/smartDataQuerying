package com.smartdataquerying.service;

import com.smartdataquerying.service.ExcelTypeInferenceService.CellValue;
import com.smartdataquerying.service.ExcelTypeInferenceService.ExcelColumnType;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ExcelTableWriter {
    private final DataSource dataSource;
    private final ExcelTypeInferenceService typeInferenceService;

    public ExcelTableWriter(DataSource dataSource, ExcelTypeInferenceService typeInferenceService) {
        this.dataSource = dataSource;
        this.typeInferenceService = typeInferenceService;
    }

    public void createAndInsert(String tableName, List<ExcelColumn> columns, List<List<CellValue>> rows) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(createSql(tableName, columns));
            }
            if (!rows.isEmpty()) {
                try (PreparedStatement ps = connection.prepareStatement(insertSql(tableName, columns))) {
                    for (List<CellValue> row : rows) {
                        for (int i = 0; i < columns.size(); i++) {
                            CellValue value = i < row.size() ? row.get(i) : null;
                            Object converted = value == null ? null : typeInferenceService.convert(value, columns.get(i).type());
                            ps.setObject(i + 1, converted);
                        }
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            }
            connection.commit();
        }
    }

    public void dropQuietly(String tableName) {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("drop table if exists `" + tableName + "`");
        } catch (Exception ignored) {
        }
    }

    private String createSql(String tableName, List<ExcelColumn> columns) {
        String columnSql = columns.stream()
                .map(column -> quoteIdentifier(column.physicalName()) + " " + column.type().sqlType())
                .collect(Collectors.joining(", "));
        return "create table " + quoteIdentifier(tableName) + " (id bigint primary key auto_increment"
                + (columnSql.isBlank() ? "" : ", " + columnSql) + ")";
    }

    private String insertSql(String tableName, List<ExcelColumn> importColumns) {
        String columns = importColumns.stream()
                .map(column -> quoteIdentifier(column.physicalName()))
                .collect(Collectors.joining(", "));
        String placeholders = importColumns.stream().map(column -> "?").collect(Collectors.joining(", "));
        return "insert into " + quoteIdentifier(tableName) + " (" + columns + ") values (" + placeholders + ")";
    }

    private String quoteIdentifier(String identifier) {
        return "`" + identifier.replace("`", "``") + "`";
    }

    public record ExcelColumn(String physicalName, String originalHeader, ExcelColumnType type) {
    }
}
