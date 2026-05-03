package com.smartdataquerying.service;

import com.smartdataquerying.model.BusinessTerm;
import com.smartdataquerying.model.DatasourceColumn;
import com.smartdataquerying.model.DatasourceConfig;
import com.smartdataquerying.model.DatasourceTable;
import com.smartdataquerying.model.SqlExample;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PromptService {
    public String build(DatasourceConfig datasource, List<DatasourceTable> tables,
                        List<BusinessTerm> terms, List<SqlExample> examples, String question) {
        String schema = tables.stream()
                .filter(table -> table.enabled)
                .map(this::tableBlock)
                .collect(Collectors.joining("\n\n"));
        String termBlock = terms.stream()
                .map(term -> "- " + term.name + ": " + term.definitionText
                        + optional(" Synonyms: ", term.synonyms)
                        + optional(" Calculation: ", term.calculation))
                .collect(Collectors.joining("\n"));
        String exampleBlock = examples.stream()
                .map(example -> "Question: " + example.question + "\nSQL: " + example.sqlText)
                .collect(Collectors.joining("\n\n"));
        return """
                You generate safe SQL for %s.
                Return JSON only with fields: sql, explanation, confidence.
                Rules:
                - Generate exactly one SELECT statement.
                - Do not use INSERT, UPDATE, DELETE, DROP, ALTER, CREATE, TRUNCATE.
                - Only use tables and columns listed below.
                - Do not select columns marked sensitive or disabled.
                - Prefer clear aliases when useful.
                
                Database schema:
                %s
                
                Business terms:
                %s
                
                SQL examples:
                %s
                
                User question:
                %s
                """.formatted(datasource.type, schema, blank(termBlock), blank(exampleBlock), question);
    }

    private String tableBlock(DatasourceTable table) {
        String columns = table.columns.stream()
                .filter(column -> column.enabled)
                .map(this::columnLine)
                .collect(Collectors.joining("\n"));
        return "Table: " + qualify(table) + optional(" -- ", table.commentText) + "\n" + columns;
    }

    private String columnLine(DatasourceColumn column) {
        return "- " + column.columnName + " " + column.dataType
                + (column.sensitive ? " sensitive" : "")
                + optional(" -- ", column.commentText);
    }

    private String qualify(DatasourceTable table) {
        return table.schemaName == null || table.schemaName.isBlank()
                ? table.tableName
                : table.schemaName + "." + table.tableName;
    }

    private String optional(String prefix, String value) {
        return value == null || value.isBlank() ? "" : prefix + value;
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? "(none)" : value;
    }
}

