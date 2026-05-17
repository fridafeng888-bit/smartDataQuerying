package com.smartdataquerying.controller;

import com.smartdataquerying.common.ApiResponse;
import com.smartdataquerying.dto.KnowledgeDtos.SqlExampleRequest;
import com.smartdataquerying.dto.KnowledgeDtos.TermRequest;
import com.smartdataquerying.model.BusinessTerm;
import com.smartdataquerying.model.SqlExample;
import com.smartdataquerying.service.KnowledgeService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class KnowledgeController {
    private final KnowledgeService service;

    public KnowledgeController(KnowledgeService service) {
        this.service = service;
    }

    @GetMapping("/api/terms")
    public ApiResponse<List<BusinessTerm>> terms() {
        return ApiResponse.ok(service.terms());
    }

    @PostMapping("/api/terms")
    public ApiResponse<BusinessTerm> createTerm(@RequestBody TermRequest request) {
        return ApiResponse.ok(service.createTerm(request));
    }

    @PostMapping("/api/terms/templates/sales-opportunity")
    public ApiResponse<List<BusinessTerm>> seedSalesOpportunityTemplate() {
        return ApiResponse.ok(service.seedSalesOpportunityTemplate());
    }

    @PatchMapping("/api/terms/{id}")
    public ApiResponse<BusinessTerm> updateTerm(@PathVariable Long id, @RequestBody TermRequest request) {
        return ApiResponse.ok(service.updateTerm(id, request));
    }

    @DeleteMapping("/api/terms/{id}")
    public ApiResponse<Void> deleteTerm(@PathVariable Long id) {
        service.deleteTerm(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/sql-examples")
    public ApiResponse<List<SqlExample>> examples() {
        return ApiResponse.ok(service.examples());
    }

    @PostMapping("/api/sql-examples")
    public ApiResponse<SqlExample> createExample(@RequestBody SqlExampleRequest request) {
        return ApiResponse.ok(service.createExample(request));
    }

    @PatchMapping("/api/sql-examples/{id}")
    public ApiResponse<SqlExample> updateExample(@PathVariable Long id, @RequestBody SqlExampleRequest request) {
        return ApiResponse.ok(service.updateExample(id, request));
    }

    @DeleteMapping("/api/sql-examples/{id}")
    public ApiResponse<Void> deleteExample(@PathVariable Long id) {
        service.deleteExample(id);
        return ApiResponse.ok(null);
    }
}
