package com.smartdataquerying.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "datasource_table")
public class DatasourceTable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "datasource_id")
    public DatasourceConfig datasource;
    @Column(name = "schema_name")
    public String schemaName;
    @Column(name = "table_name")
    public String tableName;
    @Column(name = "comment_text")
    public String commentText;
    public boolean enabled = true;
    @OneToMany(mappedBy = "table", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordinalPosition asc")
    public List<DatasourceColumn> columns = new ArrayList<>();
    @Column(name = "created_at", insertable = false, updatable = false)
    public Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false)
    public Instant updatedAt;
}

