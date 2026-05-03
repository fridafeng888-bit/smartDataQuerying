package com.smartdataquerying.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartdataquerying.common.AppException;
import com.smartdataquerying.dto.ChatDtos.AskRequest;
import com.smartdataquerying.dto.ChatDtos.AskResponse;
import com.smartdataquerying.dto.ChatDtos.CreateSessionRequest;
import com.smartdataquerying.dto.ChatDtos.ValidateResponse;
import com.smartdataquerying.model.*;
import com.smartdataquerying.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatService {
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final QueryExecutionRepository executionRepository;
    private final DatasourceService datasourceService;
    private final DatasourceTableRepository tableRepository;
    private final KnowledgeService knowledgeService;
    private final PromptService promptService;
    private final LlmService llmService;
    private final SqlGuardService sqlGuardService;
    private final QueryService queryService;
    private final ObjectMapper objectMapper;

    public ChatService(ChatSessionRepository sessionRepository, ChatMessageRepository messageRepository,
                       QueryExecutionRepository executionRepository, DatasourceService datasourceService,
                       DatasourceTableRepository tableRepository, KnowledgeService knowledgeService,
                       PromptService promptService, LlmService llmService, SqlGuardService sqlGuardService,
                       QueryService queryService, ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.executionRepository = executionRepository;
        this.datasourceService = datasourceService;
        this.tableRepository = tableRepository;
        this.knowledgeService = knowledgeService;
        this.promptService = promptService;
        this.llmService = llmService;
        this.sqlGuardService = sqlGuardService;
        this.queryService = queryService;
        this.objectMapper = objectMapper;
    }

    public ChatSession createSession(CreateSessionRequest request) {
        ChatSession session = new ChatSession();
        session.title = request.title() == null || request.title().isBlank() ? "New chat" : request.title();
        return sessionRepository.save(session);
    }

    public List<ChatSession> sessions() {
        return sessionRepository.findAllByOrderByUpdatedAtDesc();
    }

    public List<ChatMessage> messages(Long sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    @Transactional
    public AskResponse ask(AskRequest request) {
        DatasourceConfig datasource = datasourceService.find(request.datasourceId());
        if (!datasource.enabled) {
            throw new AppException(HttpStatus.BAD_REQUEST, "DATASOURCE_DISABLED", "Datasource is disabled");
        }
        ChatSession session = request.sessionId() == null
                ? createSession(new CreateSessionRequest(titleFromQuestion(request.question())))
                : sessionRepository.findById(request.sessionId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Session not found"));
        List<DatasourceTable> tables = tableRepository.findByDatasourceIdOrderBySchemaNameAscTableNameAsc(datasource.id);
        String prompt = promptService.build(datasource, tables, knowledgeService.recallTerms(request.question()),
                knowledgeService.recallExamples(request.question(), datasource.id), request.question());

        saveMessage(session, "user", request.question(), null, null, null);
        QueryExecution execution = new QueryExecution();
        execution.datasource = datasource;
        execution.session = session;
        execution.question = request.question();

        try {
            JsonNode json = parseLlmJson(llmService.complete(prompt));
            String sql = json.path("sql").asText();
            String explanation = json.path("explanation").asText();
            String safeSql = sqlGuardService.validateAndRewrite(sql, tables);
            QueryService.QueryResult result = queryService.execute(datasource, safeSql);
            execution.generatedSql = safeSql;
            execution.status = QueryStatus.SUCCESS;
            execution.durationMs = result.durationMs();
            execution.rowCount = result.rows().size();
            executionRepository.save(execution);
            saveMessage(session, "assistant", explanation, safeSql, explanation, null);
            return new AskResponse(execution.id, safeSql, explanation, result.durationMs(),
                    result.rows().size(), result.columns(), result.rows());
        } catch (AppException ex) {
            execution.status = QueryStatus.REJECTED;
            execution.errorMessage = ex.getMessage();
            executionRepository.save(execution);
            saveMessage(session, "assistant", ex.getMessage(), null, null, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            execution.status = QueryStatus.FAILED;
            execution.errorMessage = ex.getMessage();
            executionRepository.save(execution);
            saveMessage(session, "assistant", ex.getMessage(), null, null, ex.getMessage());
            throw new AppException(HttpStatus.BAD_REQUEST, "ASK_FAILED", ex.getMessage());
        }
    }

    public ValidateResponse validate(Long datasourceId, String sql) {
        List<DatasourceTable> tables = tableRepository.findByDatasourceIdOrderBySchemaNameAscTableNameAsc(datasourceId);
        String safeSql = sqlGuardService.validateAndRewrite(sql, tables);
        return new ValidateResponse(true, safeSql, "SQL is safe to execute");
    }

    public List<QueryExecution> executions() {
        return executionRepository.findTop100ByOrderByCreatedAtDesc();
    }

    private void saveMessage(ChatSession session, String role, String content, String sql, String explanation, String error) {
        ChatMessage message = new ChatMessage();
        message.session = session;
        message.role = role;
        message.contentText = content;
        message.generatedSql = sql;
        message.explanation = explanation;
        message.errorMessage = error;
        messageRepository.save(message);
    }

    private JsonNode parseLlmJson(String content) throws Exception {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("(?s)^```(?:json)?\\s*", "").replaceFirst("(?s)\\s*```$", "");
        }
        JsonNode json = objectMapper.readTree(trimmed);
        if (!json.hasNonNull("sql")) {
            throw new IllegalArgumentException("LLM response does not contain sql");
        }
        return json;
    }

    private String titleFromQuestion(String question) {
        if (question == null || question.isBlank()) return "New chat";
        return question.length() > 32 ? question.substring(0, 32) : question;
    }
}

