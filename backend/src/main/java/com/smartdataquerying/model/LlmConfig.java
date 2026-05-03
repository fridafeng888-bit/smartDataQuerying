package com.smartdataquerying.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

@Entity
@Table(name = "llm_config")
public class LlmConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "base_url")
    public String baseUrl;
    public String model;
    @JsonIgnore
    @Column(name = "encrypted_api_key")
    public String encryptedApiKey;
    @Column(name = "created_at", insertable = false, updatable = false)
    public Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false)
    public Instant updatedAt;
}
