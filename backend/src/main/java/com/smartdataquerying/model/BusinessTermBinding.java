package com.smartdataquerying.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "business_term_binding")
public class BusinessTermBinding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "term_id")
    @JsonIgnore
    public BusinessTerm term;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "datasource_id")
    @JsonIgnore
    public DatasourceConfig datasource;
    @Column(name = "table_name")
    public String tableName;
    @Column(name = "column_name")
    public String columnName;
    @Column(name = "field_role")
    public String fieldRole;
    @Column(name = "filter_expression")
    public String filterExpression;
    @Column(name = "value_mappings")
    public String valueMappings;
    public int priority = 50;
    public boolean enabled = true;
    @Column(name = "created_at", insertable = false, updatable = false)
    public Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false)
    public Instant updatedAt;

    public Long getDatasourceId() {
        return datasource == null ? null : datasource.id;
    }
}
