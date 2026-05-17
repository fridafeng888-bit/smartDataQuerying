package com.smartdataquerying.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "excel_import_job")
public class ExcelImportJob {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "datasource_id")
    public DatasourceConfig datasource;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id")
    public DatasourceTable table;

    @Column(name = "original_file_name")
    public String originalFileName;

    @Column(name = "physical_table_name")
    public String physicalTableName;

    @Column(name = "display_table_name")
    public String displayTableName;

    @Column(name = "row_count")
    public int rowCount;

    @Column(name = "column_count")
    public int columnCount;

    public String status;

    @Column(name = "error_message", columnDefinition = "text")
    public String errorMessage;

    @Column(name = "created_at", insertable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    public Instant updatedAt;
}

