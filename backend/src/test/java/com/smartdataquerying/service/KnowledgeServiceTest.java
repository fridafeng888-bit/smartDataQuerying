package com.smartdataquerying.service;

import com.smartdataquerying.common.AppException;
import com.smartdataquerying.model.BusinessTerm;
import com.smartdataquerying.model.BusinessTermBinding;
import com.smartdataquerying.model.BusinessTermType;
import com.smartdataquerying.repository.BusinessTermRepository;
import com.smartdataquerying.repository.DatasourceConfigRepository;
import com.smartdataquerying.repository.SqlExampleRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeServiceTest {
    private final BusinessTermRepository termRepository = mock(BusinessTermRepository.class);
    private final SqlExampleRepository exampleRepository = mock(SqlExampleRepository.class);
    private final DatasourceConfigRepository datasourceRepository = mock(DatasourceConfigRepository.class);
    private final KnowledgeService service = new KnowledgeService(termRepository, exampleRepository, datasourceRepository);

    @Test
    void recallsSalesOpportunitySupportTermsForWinRate() {
        when(termRepository.findByEnabledTrueOrderByPriorityDescUpdatedAtDesc()).thenReturn(List.of(
                term(1, "赢单率", BusinessTermType.METRIC, "win rate,成交率"),
                term(2, "赢单", BusinessTermType.DIMENSION, "closed won,成交"),
                term(3, "有效商机", BusinessTermType.DIMENSION, "有效机会"),
                term(4, "商机状态", BusinessTermType.DIMENSION, "status"),
                term(5, "销售阶段", BusinessTermType.DIMENSION, "stage")
        ));

        List<String> names = service.recallTerms("本季度赢单率是多少").stream()
                .map(term -> term.name)
                .toList();

        assertTrue(names.contains("赢单率"));
        assertTrue(names.contains("赢单"));
        assertTrue(names.contains("有效商机"));
        assertTrue(names.contains("商机状态"));
    }

    @Test
    void mapsPipelineAliasToOpportunity() {
        when(termRepository.findByEnabledTrueOrderByPriorityDescUpdatedAtDesc()).thenReturn(List.of(
                term(1, "商机", BusinessTermType.DIMENSION, "opportunity,pipeline,机会,销售机会"),
                term(2, "预计签约金额", BusinessTermType.METRIC, "预测金额")
        ));

        List<String> names = service.recallTerms("按 pipeline 统计预测金额").stream()
                .map(term -> term.name)
                .toList();

        assertTrue(names.contains("商机"));
        assertTrue(names.contains("预计签约金额"));
    }

    @Test
    void rejectsSqlWhenMatchedMetricIgnoresConfiguredBinding() {
        BusinessTerm amount = term(1, "预计签约金额", BusinessTermType.METRIC, "预测金额");
        BusinessTermBinding binding = new BusinessTermBinding();
        binding.tableName = "opportunity";
        binding.columnName = "expected_amount";
        binding.enabled = true;
        amount.bindings.add(binding);

        assertDoesNotThrow(() -> service.validateGeneratedSql(
                "预计签约金额是多少",
                "select expected_amount from opportunity limit 100",
                List.of(amount)));

        assertThrows(AppException.class, () -> service.validateGeneratedSql(
                "预计签约金额是多少",
                "select actual_amount from opportunity limit 100",
                List.of(amount)));
    }

    private BusinessTerm term(long id, String name, BusinessTermType type, String aliases) {
        BusinessTerm term = new BusinessTerm();
        term.id = id;
        term.name = name;
        term.domain = "销售商机";
        term.category = "销售商机";
        term.termType = type;
        term.aliases = aliases;
        term.synonyms = aliases;
        term.definitionText = name;
        term.priority = 90;
        term.enabled = true;
        return term;
    }
}
