package com.smartdataquerying.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

@Entity
@Table(name = "chat_message")
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    public ChatSession session;
    public String role;
    @Column(name = "content_text", columnDefinition = "text")
    public String contentText;
    @Column(name = "generated_sql", columnDefinition = "text")
    public String generatedSql;
    @Column(columnDefinition = "text")
    public String explanation;
    @Column(name = "error_message", columnDefinition = "text")
    public String errorMessage;
    @Column(name = "created_at", insertable = false, updatable = false)
    public Instant createdAt;
}
