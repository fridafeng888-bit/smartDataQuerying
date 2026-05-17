package com.smartdataquerying.service;

import com.smartdataquerying.config.AppProperties;
import com.smartdataquerying.model.DatasourceColumn;
import com.smartdataquerying.model.DatasourceTable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SqlGuardServiceTest {
    private final SqlGuardService guard = new SqlGuardService(new AppProperties(
            null, null, null, new AppProperties.Query(10000, 100, 1000), null, null));

    @Test
    void appendsDefaultLimit() {
        DatasourceTable table = table("orders", column("id", false));
        String sql = guard.validateAndRewrite("select id from orders", List.of(table));
        assertTrue(sql.toLowerCase().contains("limit 100"));
    }

    @Test
    void rejectsDelete() {
        DatasourceTable table = table("orders", column("id", false));
        assertThrows(RuntimeException.class, () -> guard.validateAndRewrite("delete from orders", List.of(table)));
    }

    @Test
    void rejectsSensitiveColumn() {
        DatasourceTable table = table("users", column("phone", true));
        assertThrows(RuntimeException.class, () -> guard.validateAndRewrite("select phone from users", List.of(table)));
    }

    @Test
    void acceptsBacktickQuotedTableName() {
        DatasourceTable table = table("imp_20260504082451_a2072993", column("订单金额", false));
        String sql = guard.validateAndRewrite("select `订单金额` from `imp_20260504082451_a2072993`", List.of(table));
        assertTrue(sql.toLowerCase().contains("limit 100"));
    }

    private DatasourceTable table(String name, DatasourceColumn... columns) {
        DatasourceTable table = new DatasourceTable();
        table.tableName = name;
        table.enabled = true;
        table.columns.addAll(List.of(columns));
        return table;
    }

    private DatasourceColumn column(String name, boolean sensitive) {
        DatasourceColumn column = new DatasourceColumn();
        column.columnName = name;
        column.dataType = "varchar";
        column.enabled = true;
        column.sensitive = sensitive;
        return column;
    }
}
