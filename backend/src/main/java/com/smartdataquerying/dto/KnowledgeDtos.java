package com.smartdataquerying.dto;

public class KnowledgeDtos {
    public record TermRequest(String name, String synonyms, String definitionText, String calculation, boolean enabled) {
    }

    public record SqlExampleRequest(String question, String sqlText, String descriptionText, Long datasourceId, boolean enabled) {
    }
}

