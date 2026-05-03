package com.smartdataquerying.model;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

@Entity
@Table(name = "datasource_config")
public class DatasourceConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String name;
    @Enumerated(EnumType.STRING)
    public DatasourceType type;
    public String host;
    public Integer port;
    @Column(name = "database_name")
    public String databaseName;
    public String username;
    @JsonIgnore
    @Column(name = "encrypted_password")
    public String encryptedPassword;
    @Column(name = "ssl_enabled")
    public boolean sslEnabled;
    public boolean enabled = true;
    @Column(name = "created_at", insertable = false, updatable = false)
    public Instant createdAt;
    @Column(name = "updated_at", insertable = false, updatable = false)
    public Instant updatedAt;
}
