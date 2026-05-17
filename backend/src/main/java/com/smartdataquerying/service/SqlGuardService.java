package com.smartdataquerying.service;

import com.smartdataquerying.common.AppException;
import com.smartdataquerying.config.AppProperties;
import com.smartdataquerying.model.DatasourceColumn;
import com.smartdataquerying.model.DatasourceTable;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SqlGuardService {
    private static final Pattern LIMIT_PATTERN = Pattern.compile("(?i)\\blimit\\s+(\\d+)\\b");
    private final AppProperties properties;

    public SqlGuardService(AppProperties properties) {
        this.properties = properties;
    }

    public String validateAndRewrite(String sql, List<DatasourceTable> tables) {
        if (sql == null || sql.isBlank()) {
            throw rejected("SQL is empty");
        }
        if (sql.trim().contains(";")) {
            throw rejected("Multiple statements are not allowed");
        }
        Statement statement;
        try {
            statement = CCJSqlParserUtil.parse(sql);
        } catch (Exception ex) {
            throw rejected("SQL parse failed: " + ex.getMessage());
        }
        if (!(statement instanceof Select)) {
            throw rejected("Only SELECT statements are allowed");
        }
        Set<String> enabledTables = new HashSet<>();
        Set<String> sensitiveColumns = new HashSet<>();
        for (DatasourceTable table : tables) {
            if (!table.enabled) continue;
            enabledTables.add(normalizeIdentifier(table.tableName));
            if (table.schemaName != null) {
                enabledTables.add(normalizeIdentifier(table.schemaName + "." + table.tableName));
            }
            for (DatasourceColumn column : table.columns) {
                if (column.sensitive || !column.enabled) {
                    sensitiveColumns.add(normalizeIdentifier(column.columnName));
                }
            }
        }
        List<String> referencedTables = new TablesNamesFinder().getTableList(statement);
        for (String table : referencedTables) {
            if (!enabledTables.contains(normalizeIdentifier(table))) {
                throw rejected("Table is not enabled or unknown: " + table);
            }
        }
        String lowered = sql.toLowerCase(Locale.ROOT);
        for (String column : sensitiveColumns) {
            if (Pattern.compile("(?i)(^|[^a-z0-9_])" + Pattern.quote(column) + "([^a-z0-9_]|$)").matcher(lowered).find()) {
                throw rejected("Sensitive or disabled column cannot be queried: " + column);
            }
        }
        return rewriteLimit(sql.trim());
    }

    private String rewriteLimit(String sql) {
        int defaultLimit = properties.query().defaultLimit();
        int maxLimit = properties.query().maxLimit();
        Matcher matcher = LIMIT_PATTERN.matcher(sql);
        if (matcher.find()) {
            int requested = Integer.parseInt(matcher.group(1));
            if (requested > maxLimit) {
                return matcher.replaceFirst("LIMIT " + maxLimit);
            }
            return sql;
        }
        return sql + " LIMIT " + defaultLimit;
    }

    private AppException rejected(String message) {
        return new AppException(HttpStatus.BAD_REQUEST, "SQL_REJECTED", message);
    }

    private String normalizeIdentifier(String identifier) {
        if (identifier == null) {
            return "";
        }
        return identifier
                .replace("`", "")
                .replace("\"", "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
