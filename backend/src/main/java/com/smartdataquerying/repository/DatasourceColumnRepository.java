package com.smartdataquerying.repository;

import com.smartdataquerying.model.DatasourceColumn;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatasourceColumnRepository extends JpaRepository<DatasourceColumn, Long> {
}

