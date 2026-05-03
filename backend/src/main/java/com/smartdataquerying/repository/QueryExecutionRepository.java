package com.smartdataquerying.repository;

import com.smartdataquerying.model.QueryExecution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QueryExecutionRepository extends JpaRepository<QueryExecution, Long> {
    List<QueryExecution> findTop100ByOrderByCreatedAtDesc();
}

