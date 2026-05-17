package com.smartdataquerying.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "business_term")
public class BusinessTerm {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String name;
    public String category = "销售商机";
    @Enumerated(EnumType.STRING)
    @Column(name = "term_type")
    public BusinessTermType termType = BusinessTermType.DIMENSION;
    public String domain = "销售商机";
    public String synonyms;
    public String aliases;
    @Column(name = "definition_text")
    public String definitionText;
    public String calculation;
    @Column(name = "business_rules")
    public String businessRules;
    public int priority = 50;
    public String owner;
    public boolean verified = false;
    public boolean enabled = true;
    @OneToMany(mappedBy = "term", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("priority desc, id asc")
    public List<BusinessTermBinding> bindings = new ArrayList<>();
    @Column(name = "created_at", insertable = false, updatable = false)
    public Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false)
    public Instant updatedAt;
}
