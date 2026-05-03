package com.smartdataquerying.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

@Entity
@Table(name = "query_execution")
public class QueryExecution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "datasource_id")
    public DatasourceConfig datasource;
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    public ChatSession session;
    @Column(columnDefinition = "text")
    public String question;
    @Column(name = "generated_sql", columnDefinition = "text")
    public String generatedSql;
    @Enumerated(EnumType.STRING)
    public QueryStatus status;
    @Column(name = "duration_ms")
    public Long durationMs;
    @Column(name = "row_count")
    public Integer rowCount;
    @Column(name = "error_message", columnDefinition = "text")
    public String errorMessage;
    @Column(name = "created_at", insertable = false, updatable = false)
    public Instant createdAt;
}
