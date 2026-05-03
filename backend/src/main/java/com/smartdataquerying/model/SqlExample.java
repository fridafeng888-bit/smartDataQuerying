package com.smartdataquerying.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

@Entity
@Table(name = "sql_example")
public class SqlExample {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(columnDefinition = "text")
    public String question;
    @Column(name = "sql_text", columnDefinition = "text")
    public String sqlText;
    @Column(name = "description_text", columnDefinition = "text")
    public String descriptionText;
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "datasource_id")
    public DatasourceConfig datasource;
    public boolean enabled = true;
    @Column(name = "created_at", insertable = false, updatable = false)
    public Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false)
    public Instant updatedAt;
}
