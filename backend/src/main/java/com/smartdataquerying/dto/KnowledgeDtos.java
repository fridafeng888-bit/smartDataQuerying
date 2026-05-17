package com.smartdataquerying.dto;

import com.smartdataquerying.model.BusinessTermType;

import java.util.List;

public class KnowledgeDtos {
    public record TermRequest(
            String name,
            String category,
            String domain,
            BusinessTermType termType,
            String synonyms,
            String aliases,
            String definitionText,
            String calculation,
            String businessRules,
            Integer priority,
            String owner,
            Boolean verified,
            boolean enabled,
            List<TermBindingRequest> bindings
    ) {
    }

    public record TermBindingRequest(
            Long id,
            Long datasourceId,
            String tableName,
            String columnName,
            String fieldRole,
            String filterExpression,
            String valueMappings,
            Integer priority,
            Boolean enabled
    ) {
    }

    public record SqlExampleRequest(String question, String sqlText, String descriptionText, Long datasourceId, boolean enabled) {
    }
}
