package com.smartdataquerying.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "business_term")
public class BusinessTerm {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String name;
    public String synonyms;
    @Column(name = "definition_text")
    public String definitionText;
    public String calculation;
    public boolean enabled = true;
    @Column(name = "created_at", insertable = false, updatable = false)
    public Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false)
    public Instant updatedAt;
}

