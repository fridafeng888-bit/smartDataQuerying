package com.smartdataquerying.service;

import com.smartdataquerying.config.AppProperties;
import com.smartdataquerying.model.DatasourceConfig;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.*;

@Service
public class QueryService {
    private final DatasourceConnectionFactory connectionFactory;
    private final AppProperties properties;

    public QueryService(DatasourceConnectionFactory connectionFactory, AppProperties properties) {
        this.connectionFactory = connectionFactory;
        this.properties = properties;
    }

    public QueryResult execute(DatasourceConfig datasource, String sql) throws Exception {
        try (Connection connection = connectionFactory.open(datasource);
             Statement statement = connection.createStatement()) {
            statement.setQueryTimeout((int) Math.ceil(properties.query().timeoutMs() / 1000.0));
            long start = System.currentTimeMillis();
            try (ResultSet rs = statement.executeQuery(sql)) {
                ResultSetMetaData meta = rs.getMetaData();
                List<String> columns = new ArrayList<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    columns.add(meta.getColumnLabel(i));
                }
                List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (String column : columns) {
                        row.put(column, rs.getObject(column));
                    }
                    rows.add(row);
                }
                return new QueryResult(columns, rows, System.currentTimeMillis() - start);
            }
        }
    }

    public record QueryResult(List<String> columns, List<Map<String, Object>> rows, long durationMs) {
    }
}

