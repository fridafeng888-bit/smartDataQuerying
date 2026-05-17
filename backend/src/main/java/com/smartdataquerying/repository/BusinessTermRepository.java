package com.smartdataquerying.repository;

import com.smartdataquerying.model.BusinessTerm;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BusinessTermRepository extends JpaRepository<BusinessTerm, Long> {
    @EntityGraph(attributePaths = "bindings")
    List<BusinessTerm> findByEnabledTrueOrderByPriorityDescUpdatedAtDesc();

    @EntityGraph(attributePaths = "bindings")
    List<BusinessTerm> findAllByOrderByDomainAscPriorityDescNameAsc();

    Optional<BusinessTerm> findByNameAndDomain(String name, String domain);
}
