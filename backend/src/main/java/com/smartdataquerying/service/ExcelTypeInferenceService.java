package com.smartdataquerying.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class ExcelTypeInferenceService {
    public ExcelColumnType infer(List<CellValue> values) {
        List<CellValue> nonBlank = values.stream().filter(value -> !value.blank()).toList();
        if (nonBlank.isEmpty()) {
            return ExcelColumnType.VARCHAR;
        }
        if (nonBlank.stream().allMatch(this::isBoolean)) {
            return ExcelColumnType.BOOLEAN;
        }
        if (nonBlank.stream().allMatch(this::isNumber)) {
            return ExcelColumnType.DECIMAL;
        }
        if (nonBlank.stream().allMatch(this::isDate)) {
            return ExcelColumnType.DATETIME;
        }
        if (nonBlank.stream().anyMatch(value -> value.asString().length() > 1024)) {
            return ExcelColumnType.TEXT;
        }
        return ExcelColumnType.VARCHAR;
    }

    public Object convert(CellValue value, ExcelColumnType type) {
        if (value.blank()) {
            return null;
        }
        return switch (type) {
            case BOOLEAN -> parseBoolean(value.asString());
            case DECIMAL -> parseDecimal(value);
            case DATETIME -> parseDate(value);
            case TEXT, VARCHAR -> value.asString();
        };
    }

    private boolean isBoolean(CellValue value) {
        String text = value.asString().trim().toLowerCase();
        return text.equals("true") || text.equals("false") || text.equals("yes") || text.equals("no")
                || text.equals("1") || text.equals("0") || text.equals("是") || text.equals("否");
    }

    private boolean isNumber(CellValue value) {
        if (value.numeric() != null && !value.dateCell()) {
            return true;
        }
        try {
            new BigDecimal(value.asString().trim());
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isDate(CellValue value) {
        if (value.dateCell()) {
            return true;
        }
        try {
            LocalDateTime.parse(value.asString().trim().replace(" ", "T"));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private Boolean parseBoolean(String text) {
        String normalized = text.trim().toLowerCase();
        return normalized.equals("true") || normalized.equals("yes") || normalized.equals("1") || normalized.equals("是");
    }

    private BigDecimal parseDecimal(CellValue value) {
        if (value.numeric() != null) {
            return BigDecimal.valueOf(value.numeric());
        }
        return new BigDecimal(value.asString().trim());
    }

    private LocalDateTime parseDate(CellValue value) {
        if (value.dateCell() && value.cell() != null) {
            return value.cell().getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        }
        return LocalDateTime.parse(value.asString().trim().replace(" ", "T"));
    }

    public enum ExcelColumnType {
        BOOLEAN("boolean"),
        DECIMAL("decimal(20,6)"),
        DATETIME("datetime"),
        VARCHAR("text"),
        TEXT("text");

        private final String sqlType;

        ExcelColumnType(String sqlType) {
            this.sqlType = sqlType;
        }

        public String sqlType() {
            return sqlType;
        }
    }

    public record CellValue(Cell cell, String asString, Double numeric, boolean dateCell) {
        public boolean blank() {
            return asString == null || asString.isBlank();
        }
    }
}
