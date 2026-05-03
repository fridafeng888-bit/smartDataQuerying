package com.smartdataquerying.service;

import com.smartdataquerying.common.AppException;
import com.smartdataquerying.dto.KnowledgeDtos.SqlExampleRequest;
import com.smartdataquerying.dto.KnowledgeDtos.TermRequest;
import com.smartdataquerying.model.BusinessTerm;
import com.smartdataquerying.model.SqlExample;
import com.smartdataquerying.repository.BusinessTermRepository;
import com.smartdataquerying.repository.DatasourceConfigRepository;
import com.smartdataquerying.repository.SqlExampleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class KnowledgeService {
    private final BusinessTermRepository termRepository;
    private final SqlExampleRepository exampleRepository;
    private final DatasourceConfigRepository datasourceRepository;

    public KnowledgeService(BusinessTermRepository termRepository, SqlExampleRepository exampleRepository,
                            DatasourceConfigRepository datasourceRepository) {
        this.termRepository = termRepository;
        this.exampleRepository = exampleRepository;
        this.datasourceRepository = datasourceRepository;
    }

    public List<BusinessTerm> terms() {
        return termRepository.findAll();
    }

    public BusinessTerm createTerm(TermRequest request) {
        BusinessTerm term = new BusinessTerm();
        apply(term, request);
        return termRepository.save(term);
    }

    public BusinessTerm updateTerm(Long id, TermRequest request) {
        BusinessTerm term = termRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "TERM_NOT_FOUND", "Term not found"));
        apply(term, request);
        return termRepository.save(term);
    }

    public void deleteTerm(Long id) {
        termRepository.deleteById(id);
    }

    public List<SqlExample> examples() {
        return exampleRepository.findAll();
    }

    public SqlExample createExample(SqlExampleRequest request) {
        SqlExample example = new SqlExample();
        apply(example, request);
        return exampleRepository.save(example);
    }

    public SqlExample updateExample(Long id, SqlExampleRequest request) {
        SqlExample example = exampleRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "EXAMPLE_NOT_FOUND", "SQL example not found"));
        apply(example, request);
        return exampleRepository.save(example);
    }

    public void deleteExample(Long id) {
        exampleRepository.deleteById(id);
    }

    public List<BusinessTerm> recallTerms(String question) {
        String q = normalize(question);
        return termRepository.findByEnabledTrue().stream()
                .filter(term -> contains(q, term.name) || containsAny(q, term.synonyms))
                .limit(8)
                .toList();
    }

    public List<SqlExample> recallExamples(String question, Long datasourceId) {
        String q = normalize(question);
        return exampleRepository.findByEnabledTrue().stream()
                .filter(example -> example.datasource == null || example.datasource.id.equals(datasourceId))
                .sorted(Comparator.comparingInt(example -> -score(q, example.question)))
                .filter(example -> score(q, example.question) > 0)
                .limit(5)
                .toList();
    }

    private void apply(BusinessTerm term, TermRequest request) {
        term.name = request.name();
        term.synonyms = request.synonyms();
        term.definitionText = request.definitionText();
        term.calculation = request.calculation();
        term.enabled = request.enabled();
    }

    private void apply(SqlExample example, SqlExampleRequest request) {
        example.question = request.question();
        example.sqlText = request.sqlText();
        example.descriptionText = request.descriptionText();
        example.enabled = request.enabled();
        example.datasource = request.datasourceId() == null ? null : datasourceRepository.findById(request.datasourceId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "DATASOURCE_NOT_FOUND", "Datasource not found"));
    }

    private boolean contains(String question, String value) {
        return value != null && !value.isBlank() && question.contains(normalize(value));
    }

    private boolean containsAny(String question, String values) {
        if (values == null) return false;
        for (String value : values.split("[,，;；\\s]+")) {
            if (contains(question, value)) return true;
        }
        return false;
    }

    private int score(String question, String candidate) {
        int score = 0;
        for (String token : normalize(candidate).split("[,，;；\\s]+")) {
            if (!token.isBlank() && question.contains(token)) score++;
        }
        return score;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}

