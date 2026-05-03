package com.smartdataquerying.repository;

import com.smartdataquerying.model.SqlExample;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SqlExampleRepository extends JpaRepository<SqlExample, Long> {
    List<SqlExample> findByEnabledTrue();
}

