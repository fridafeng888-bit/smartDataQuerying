package com.smartdataquerying.repository;

import com.smartdataquerying.model.BusinessTerm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BusinessTermRepository extends JpaRepository<BusinessTerm, Long> {
    List<BusinessTerm> findByEnabledTrue();
}

