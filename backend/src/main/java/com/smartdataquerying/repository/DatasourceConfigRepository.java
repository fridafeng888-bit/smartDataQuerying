package com.smartdataquerying.repository;

import com.smartdataquerying.model.DatasourceConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatasourceConfigRepository extends JpaRepository<DatasourceConfig, Long> {
}

