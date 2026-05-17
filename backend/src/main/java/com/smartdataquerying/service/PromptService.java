package com.smartdataquerying.service;

import com.smartdataquerying.model.*;
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
                .map(this::termBlock)
                .collect(Collectors.joining("\n\n"));
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
                - Business terms are authoritative semantic assets. Apply their field bindings, filters, formulas, value mappings, and business rules before guessing.
                - For sales opportunity questions, prefer terms in the 销售商机 domain and use configured term bindings to choose tables and columns.
                - If a metric term has a calculation, implement the metric exactly with the mapped fields.
                - If a term defines filters or value mappings, apply them in WHERE/CASE logic when relevant.
                - If a user question conflicts with a business term, follow the business term and explain that choice.
                - Do not select columns marked sensitive or disabled.
                - If a table or column name contains Chinese characters, spaces, punctuation, or reserved words, quote it with backticks.
                - Prefer clear aliases when useful.

                Database schema:
                %s

                Business semantic assets:
                %s

                SQL examples:
                %s

                User question:
                %s
                """.formatted(datasource.type, blank(schema), blank(termBlock), blank(exampleBlock), question);
    }

    private String tableBlock(DatasourceTable table) {
        String columns = table.columns.stream()
                .filter(column -> column.enabled)
                .map(this::columnLine)
                .collect(Collectors.joining("\n"));
        return "Table: " + qualify(table)
                + optional(" -- display name: ", table.commentText)
                + "\n" + columns;
    }

    private String columnLine(DatasourceColumn column) {
        return "- " + column.columnName + " " + column.dataType
                + (column.sensitive ? " sensitive" : "")
                + optional(" -- ", column.commentText);
    }

    private String termBlock(BusinessTerm term) {
        String bindings = term.bindings.stream()
                .filter(binding -> binding.enabled)
                .map(this::bindingLine)
                .collect(Collectors.joining("\n"));
        return "- " + term.name
                + "\n  domain: " + value(term.domain)
                + "\n  type: " + value(term.termType)
                + "\n  aliases: " + value(firstNonBlank(term.aliases, term.synonyms))
                + "\n  definition: " + value(term.definitionText)
                + optional("\n  calculation: ", term.calculation)
                + optional("\n  business_rules: ", term.businessRules)
                + optional("\n  bindings:\n", bindings);
    }

    private String bindingLine(BusinessTermBinding binding) {
        return "    - "
                + "table=" + value(binding.tableName)
                + ", column=" + value(binding.columnName)
                + ", role=" + value(binding.fieldRole)
                + optional(", filter=", binding.filterExpression)
                + optional(", values=", binding.valueMappings);
    }

    private String qualify(DatasourceTable table) {
        return table.schemaName == null || table.schemaName.isBlank()
                ? table.tableName
                : table.schemaName + "." + table.tableName;
    }

    private String optional(String prefix, String value) {
        return value == null || value.isBlank() ? "" : prefix + value;
    }

    private String value(Object value) {
        return value == null || value.toString().isBlank() ? "(none)" : value.toString();
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? "(none)" : value;
    }

    private String firstNonBlank(String left, String right) {
        return left == null || left.isBlank() ? right : left;
    }
}
