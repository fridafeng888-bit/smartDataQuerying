package com.smartdataquerying.service;

import com.smartdataquerying.common.AppException;
import com.smartdataquerying.dto.KnowledgeDtos.SqlExampleRequest;
import com.smartdataquerying.dto.KnowledgeDtos.TermBindingRequest;
import com.smartdataquerying.dto.KnowledgeDtos.TermRequest;
import com.smartdataquerying.model.*;
import com.smartdataquerying.repository.BusinessTermRepository;
import com.smartdataquerying.repository.DatasourceConfigRepository;
import com.smartdataquerying.repository.SqlExampleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class KnowledgeService {
    private static final String SALES_DOMAIN = "销售商机";
    private static final String GENERAL_DOMAIN = "通用";
    private static final int MAX_RECALLED_TERMS = 24;
    private static final int MAX_EXAMPLES = 5;
    private static final Map<BusinessTermType, Integer> TYPE_WEIGHTS = Map.of(
            BusinessTermType.METRIC, 20,
            BusinessTermType.DIMENSION, 14,
            BusinessTermType.ORG, 12
    );

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
        return termRepository.findAllByOrderByDomainAscPriorityDescNameAsc();
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

    public List<BusinessTerm> seedSalesOpportunityTemplate() {
        List<TermTemplate> templates = salesOpportunityTemplates();
        List<BusinessTerm> createdOrExisting = new ArrayList<>();
        for (TermTemplate template : templates) {
            BusinessTerm term = termRepository.findByNameAndDomain(template.name(), SALES_DOMAIN)
                    .orElseGet(BusinessTerm::new);
            if (term.id == null) {
                term.name = template.name();
                term.domain = SALES_DOMAIN;
                term.category = SALES_DOMAIN;
                term.termType = template.termType();
                term.aliases = template.aliases();
                term.synonyms = template.aliases();
                term.definitionText = template.definition();
                term.calculation = template.calculation();
                term.businessRules = template.businessRules();
                term.priority = template.priority();
                term.owner = "系统模板";
                term.verified = false;
                term.enabled = true;
                createdOrExisting.add(termRepository.save(term));
            } else {
                createdOrExisting.add(term);
            }
        }
        return createdOrExisting;
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
        List<BusinessTerm> enabledTerms = termRepository.findByEnabledTrueOrderByPriorityDescUpdatedAtDesc();
        LinkedHashMap<Long, BusinessTerm> selected = new LinkedHashMap<>();

        enabledTerms.stream()
                .map(term -> new ScoredTerm(term, scoreTerm(q, term)))
                .filter(scored -> scored.score() > 0)
                .sorted(this::compareScoredTerms)
                .limit(MAX_RECALLED_TERMS)
                .forEach(scored -> selected.put(scored.term().id, scored.term()));

        expandSalesOpportunityTerms(q, enabledTerms, selected);

        if (!selected.isEmpty()) {
            return selected.values().stream().limit(MAX_RECALLED_TERMS).toList();
        }
        return enabledTerms.stream()
                .filter(term -> SALES_DOMAIN.equals(normalizeDomain(term.domain)) || GENERAL_DOMAIN.equals(normalizeDomain(term.domain)))
                .limit(MAX_RECALLED_TERMS)
                .toList();
    }

    public List<DatasourceTable> scopeTables(List<DatasourceTable> tables, List<BusinessTerm> terms) {
        Set<String> tableNames = terms.stream()
                .flatMap(term -> term.bindings.stream())
                .filter(binding -> binding.enabled)
                .map(binding -> normalizeIdentifier(binding.tableName))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (tableNames.isEmpty()) {
            return tables;
        }
        List<DatasourceTable> scoped = tables.stream()
                .filter(table -> tableNames.contains(normalizeIdentifier(table.tableName))
                        || tableNames.contains(normalizeIdentifier(qualify(table))))
                .toList();
        return scoped.isEmpty() ? tables : scoped;
    }

    public void validateGeneratedSql(String question, String sql, List<BusinessTerm> terms) {
        String q = normalize(question);
        String normalizedSql = normalizeIdentifier(sql);
        for (BusinessTerm term : terms) {
            if (!matchesTerm(q, term)) {
                continue;
            }
            List<BusinessTermBinding> bindings = term.bindings.stream()
                    .filter(binding -> binding.enabled)
                    .filter(binding -> hasText(binding.tableName) || hasText(binding.columnName))
                    .toList();
            if (bindings.isEmpty()) {
                continue;
            }
            boolean anyBindingUsed = bindings.stream().anyMatch(binding ->
                    (!hasText(binding.tableName) || normalizedSql.contains(normalizeIdentifier(binding.tableName)))
                            && (!hasText(binding.columnName) || normalizedSql.contains(normalizeIdentifier(binding.columnName))));
            if (!anyBindingUsed) {
                throw new AppException(HttpStatus.BAD_REQUEST, "SEMANTIC_BINDING_MISMATCH",
                        "Generated SQL did not use the configured binding for business term: " + term.name);
            }
        }
    }

    public List<SqlExample> recallExamples(String question, Long datasourceId) {
        String q = normalize(question);
        return exampleRepository.findByEnabledTrue().stream()
                .filter(example -> example.datasource == null || example.datasource.id.equals(datasourceId))
                .sorted(Comparator.comparingInt(example -> -scoreText(q, example.question)))
                .filter(example -> scoreText(q, example.question) > 0)
                .limit(MAX_EXAMPLES)
                .toList();
    }

    private void apply(BusinessTerm term, TermRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "TERM_NAME_REQUIRED", "Term name is required");
        }
        String domain = normalizeDomain(firstNonBlank(request.domain(), request.category(), SALES_DOMAIN));
        String aliases = firstNonBlank(request.aliases(), request.synonyms(), "");
        term.name = request.name().trim();
        term.domain = domain;
        term.category = domain;
        term.termType = request.termType() == null ? BusinessTermType.DIMENSION : request.termType();
        term.aliases = aliases;
        term.synonyms = firstNonBlank(request.synonyms(), aliases);
        term.definitionText = firstNonBlank(request.definitionText(), "");
        term.calculation = request.calculation();
        term.businessRules = request.businessRules();
        term.priority = request.priority() == null ? 50 : request.priority();
        term.owner = request.owner();
        term.verified = request.verified() != null && request.verified();
        term.enabled = request.enabled();
        if (request.bindings() != null) {
            term.bindings.clear();
            for (TermBindingRequest bindingRequest : request.bindings()) {
                BusinessTermBinding binding = new BusinessTermBinding();
                binding.term = term;
                binding.datasource = bindingRequest.datasourceId() == null ? null : datasourceRepository.findById(bindingRequest.datasourceId())
                        .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "DATASOURCE_NOT_FOUND", "Datasource not found"));
                binding.tableName = trimToNull(bindingRequest.tableName());
                binding.columnName = trimToNull(bindingRequest.columnName());
                binding.fieldRole = trimToNull(bindingRequest.fieldRole());
                binding.filterExpression = trimToNull(bindingRequest.filterExpression());
                binding.valueMappings = trimToNull(bindingRequest.valueMappings());
                binding.priority = bindingRequest.priority() == null ? 50 : bindingRequest.priority();
                binding.enabled = bindingRequest.enabled() == null || bindingRequest.enabled();
                term.bindings.add(binding);
            }
        }
    }

    private void apply(SqlExample example, SqlExampleRequest request) {
        example.question = request.question();
        example.sqlText = request.sqlText();
        example.descriptionText = request.descriptionText();
        example.enabled = request.enabled();
        example.datasource = request.datasourceId() == null ? null : datasourceRepository.findById(request.datasourceId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "DATASOURCE_NOT_FOUND", "Datasource not found"));
    }

    private int compareScoredTerms(ScoredTerm left, ScoredTerm right) {
        int scoreCompare = Integer.compare(right.score(), left.score());
        if (scoreCompare != 0) return scoreCompare;
        int priorityCompare = Integer.compare(right.term().priority, left.term().priority);
        if (priorityCompare != 0) return priorityCompare;
        return left.term().name.compareTo(right.term().name);
    }

    private int scoreTerm(String question, BusinessTerm term) {
        int score = 0;
        if (contains(question, term.name)) score += 120;
        if (contains(question, term.domain) || contains(question, term.category)) score += 12;
        if (SALES_DOMAIN.equals(normalizeDomain(term.domain))) score += 4;
        score += containsAnyScore(question, term.aliases, 90);
        score += containsAnyScore(question, term.synonyms, 60);
        score += containsAnyScore(question, term.businessRules, 15);
        score += containsAnyScore(question, term.calculation, 12);
        score += TYPE_WEIGHTS.getOrDefault(term.termType, 0);
        for (BusinessTermBinding binding : term.bindings) {
            if (!binding.enabled) continue;
            if (contains(question, binding.tableName)) score += 20;
            if (contains(question, binding.columnName)) score += 25;
            if (contains(question, binding.fieldRole)) score += 15;
            if (contains(question, binding.valueMappings)) score += 20;
        }
        return score;
    }

    private void expandSalesOpportunityTerms(String question, List<BusinessTerm> enabledTerms,
                                             LinkedHashMap<Long, BusinessTerm> selected) {
        if (containsAnyLiteral(question, "赢单率", "转化率")) {
            addTermsByName(enabledTerms, selected, "赢单", "有效商机", "商机", "商机状态", "销售阶段");
        }
        if (containsAnyLiteral(question, "预计签约金额", "加权预测金额", "预测", "pipeline")) {
            addTermsByName(enabledTerms, selected, "商机", "预计签约金额", "预计签约日期", "销售阶段", "加权预测金额");
        }
        if (containsAnyLiteral(question, "行业", "区域", "客户", "重点客户")) {
            addTermsByName(enabledTerms, selected, "客户", "客户集团", "行业", "区域", "客户等级", "重点客户");
        }
        if (containsAnyLiteral(question, "很久没跟进", "停滞", "跟进", "下一步")) {
            addTermsByName(enabledTerms, selected, "停滞商机", "最近跟进时间", "下一步动作", "销售负责人", "商机状态");
        }
        if (containsAnyLiteral(question, "本季度", "今年", "本月", "上月", "预计签约日期")) {
            addTermsByName(enabledTerms, selected, "预计签约日期");
        }
    }

    private void addTermsByName(List<BusinessTerm> terms, LinkedHashMap<Long, BusinessTerm> selected, String... names) {
        Set<String> wanted = Arrays.stream(names).collect(Collectors.toSet());
        terms.stream()
                .filter(term -> wanted.contains(term.name))
                .sorted(Comparator.comparingInt((BusinessTerm term) -> term.priority).reversed())
                .forEach(term -> selected.putIfAbsent(term.id, term));
    }

    private boolean matchesTerm(String question, BusinessTerm term) {
        return contains(question, term.name) || containsAny(question, term.aliases) || containsAny(question, term.synonyms);
    }

    private boolean contains(String question, String value) {
        return value != null && !value.isBlank() && question.contains(normalize(value));
    }

    private boolean containsAny(String question, String values) {
        return containsAnyScore(question, values, 1) > 0;
    }

    private int containsAnyScore(String question, String values, int weight) {
        if (values == null) return 0;
        int score = 0;
        for (String value : splitTerms(values)) {
            if (contains(question, value)) score += weight;
        }
        return score;
    }

    private boolean containsAnyLiteral(String question, String... values) {
        return Arrays.stream(values).anyMatch(value -> contains(question, value));
    }

    private List<String> splitTerms(String values) {
        if (values == null) return List.of();
        return Arrays.stream(values.split("[,，;；、\\s]+"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private int scoreText(String question, String candidate) {
        int score = 0;
        for (String token : splitTerms(normalize(candidate))) {
            if (!token.isBlank() && question.contains(token)) score++;
        }
        return score;
    }

    private String normalizeDomain(String domain) {
        return domain == null || domain.isBlank() ? SALES_DOMAIN : domain.trim();
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private String normalizeIdentifier(String identifier) {
        return normalize(identifier).replace("`", "").replace("\"", "");
    }

    private String qualify(DatasourceTable table) {
        return table.schemaName == null || table.schemaName.isBlank()
                ? table.tableName
                : table.schemaName + "." + table.tableName;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private List<TermTemplate> salesOpportunityTemplates() {
        return List.of(
                new TermTemplate("线索", BusinessTermType.DIMENSION, "lead,销售线索,潜客", "尚未确认销售价值或需求的潜在客户机会。", null, null, 80),
                new TermTemplate("有效线索", BusinessTermType.DIMENSION, "qualified lead,MQL,SQL", "满足公司定义的可跟进条件的线索。", null, "排除无效、重复、取消、测试线索。", 86),
                new TermTemplate("线索来源", BusinessTermType.DIMENSION, "来源,渠道,获客渠道", "线索进入销售流程的来源渠道。", null, null, 70),
                new TermTemplate("线索转化率", BusinessTermType.METRIC, "lead conversion,线索转商机率", "线索转化为商机的比例。", "转化商机数 / 有效线索数", null, 92),
                new TermTemplate("商机", BusinessTermType.DIMENSION, "opportunity,pipeline,机会,销售机会", "已识别客户需求、金额或服务范围的销售机会。", null, null, 95),
                new TermTemplate("有效商机", BusinessTermType.DIMENSION, "有效机会,有效pipeline", "进入正式销售流程且未被判定无效的商机。", null, "排除删除、测试、无效、重复商机。", 94),
                new TermTemplate("销售阶段", BusinessTermType.DIMENSION, "商机阶段,阶段,pipeline stage", "商机在销售漏斗中的当前阶段。", null, "常见阶段包括需求确认、方案、投标、谈判、赢单、输单。", 90),
                new TermTemplate("商机状态", BusinessTermType.DIMENSION, "机会状态,status", "商机当前状态，用于区分进行中、赢单、输单、暂停或取消。", null, null, 88),
                new TermTemplate("预计签约金额", BusinessTermType.METRIC, "预计金额,商机金额,预测金额,预估合同额", "商机当前预计可签约的合同金额。", null, null, 96),
                new TermTemplate("预计签约日期", BusinessTermType.DIMENSION, "预计签单日期,预计成交日期,close date", "商机预计完成签约或成交的日期。", null, null, 82),
                new TermTemplate("客户", BusinessTermType.DIMENSION, "client,客户名称,公司", "销售商机关联的客户主体。", null, null, 88),
                new TermTemplate("客户集团", BusinessTermType.DIMENSION, "集团客户,母公司,客户母集团", "客户所属集团或统一管理的客户群。", null, null, 72),
                new TermTemplate("行业", BusinessTermType.DIMENSION, "industry,客户行业", "客户所属行业。", null, null, 84),
                new TermTemplate("区域", BusinessTermType.DIMENSION, "region,地区,城市,省份", "客户或销售归属区域。", null, null, 78),
                new TermTemplate("客户等级", BusinessTermType.DIMENSION, "客户级别,客户分层", "客户价值或经营优先级分层。", null, null, 76),
                new TermTemplate("重点客户", BusinessTermType.DIMENSION, "KA,key account,战略客户", "被公司标记为重点经营的客户。", null, null, 82),
                new TermTemplate("销售负责人", BusinessTermType.ORG, "owner,销售,客户经理,负责人", "商机的主要销售负责人。", null, null, 86),
                new TermTemplate("跟进记录", BusinessTermType.DIMENSION, "follow-up,拜访记录,沟通记录", "销售人员对线索或商机的跟进行为记录。", null, null, 74),
                new TermTemplate("最近跟进时间", BusinessTermType.DIMENSION, "最后跟进时间,last activity,最近联系时间", "商机最近一次被销售跟进或更新的时间。", null, null, 86),
                new TermTemplate("下一步动作", BusinessTermType.DIMENSION, "next step,下一步计划", "销售负责人记录的下一步推进事项。", null, null, 68),
                new TermTemplate("停滞商机", BusinessTermType.DIMENSION, "沉默商机,长期未跟进,很久没跟进", "超过设定天数没有跟进且仍未关闭的商机。", null, "默认理解为最近 30 天无跟进且状态不是赢单或输单。", 91),
                new TermTemplate("赢单", BusinessTermType.DIMENSION, "closed won,成交,签约成功", "商机最终成交或签约成功。", null, null, 90),
                new TermTemplate("输单", BusinessTermType.DIMENSION, "closed lost,丢单,未成交", "商机最终未成交。", null, null, 84),
                new TermTemplate("赢单率", BusinessTermType.METRIC, "win rate,成交率", "有效商机中最终赢单的比例。", "赢单商机数 / 有效商机数", "分母默认不包含无效、重复、测试商机。", 98),
                new TermTemplate("转化率", BusinessTermType.METRIC, "conversion rate,转化", "从一个销售阶段进入下一个阶段或转为赢单的比例。", "目标阶段数量 / 来源阶段数量", null, 88),
                new TermTemplate("销售漏斗", BusinessTermType.METRIC, "funnel,pipeline funnel", "按销售阶段统计商机数量和金额，用于观察转化结构。", null, null, 92),
                new TermTemplate("加权预测金额", BusinessTermType.METRIC, "weighted pipeline,加权金额,预测收入", "按阶段赢率加权后的商机预测金额。", "预计签约金额 * 阶段赢率", null, 96),
                new TermTemplate("咨询服务线", BusinessTermType.DIMENSION, "service line,业务线,服务类型", "咨询公司提供服务的专业方向或业务线。", null, null, 78),
                new TermTemplate("项目类型", BusinessTermType.DIMENSION, "咨询项目类型,服务项目类型", "潜在咨询项目的类型，如战略、运营、人力、IT、财务等。", null, null, 72),
                new TermTemplate("需求类型", BusinessTermType.DIMENSION, "客户需求,需求分类", "客户咨询需求的业务类型或问题类别。", null, null, 72),
                new TermTemplate("方案阶段", BusinessTermType.DIMENSION, "出方案,方案沟通,proposal", "商机已进入方案设计、报价或方案沟通阶段。", null, null, 76),
                new TermTemplate("投标阶段", BusinessTermType.DIMENSION, "bid,招投标,投标", "商机处于招投标相关阶段。", null, null, 74),
                new TermTemplate("立项可能性", BusinessTermType.METRIC, "签约概率,赢率,probability", "商机预计能转化为正式项目或合同的概率。", null, null, 80)
        );
    }

    private record ScoredTerm(BusinessTerm term, int score) {
    }

    private record TermTemplate(String name, BusinessTermType termType, String aliases, String definition,
                                String calculation, String businessRules, int priority) {
    }
}
