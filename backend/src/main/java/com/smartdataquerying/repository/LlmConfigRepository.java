package com.smartdataquerying.repository;

import com.smartdataquerying.model.LlmConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LlmConfigRepository extends JpaRepository<LlmConfig, Long> {
    Optional<LlmConfig> findFirstByOrderByIdAsc();
}

