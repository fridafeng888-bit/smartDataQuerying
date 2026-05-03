package com.smartdataquerying.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "datasource_column")
public class DatasourceColumn {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id")
    public DatasourceTable table;
    @Column(name = "column_name")
    public String columnName;
    @Column(name = "data_type")
    public String dataType;
    @Column(name = "nullable_col")
    public boolean nullableCol;
    @Column(name = "comment_text")
    public String commentText;
    @Column(name = "sensitive_flag")
    public boolean sensitive;
    public boolean enabled = true;
    @Column(name = "ordinal_position")
    public int ordinalPosition;
    @Column(name = "created_at", insertable = false, updatable = false)
    public Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false)
    public Instant updatedAt;
}
