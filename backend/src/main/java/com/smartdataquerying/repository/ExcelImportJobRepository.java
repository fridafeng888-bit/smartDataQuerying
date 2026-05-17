package com.smartdataquerying.repository;

import com.smartdataquerying.model.ExcelImportJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExcelImportJobRepository extends JpaRepository<ExcelImportJob, Long> {
    List<ExcelImportJob> findTop100ByOrderByCreatedAtDesc();
}

