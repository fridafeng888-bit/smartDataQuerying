package com.smartdataquerying.controller;

import com.smartdataquerying.common.ApiResponse;
import com.smartdataquerying.dto.DatasourceDtos.*;
import com.smartdataquerying.service.DatasourceService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/datasources")
public class DatasourceController {
    private final DatasourceService service;

    public DatasourceController(DatasourceService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<DatasourceResponse>> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping
    public ApiResponse<DatasourceResponse> create(@RequestBody DatasourceRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<DatasourceResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<DatasourceResponse> update(@PathVariable Long id, @RequestBody DatasourceRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/test")
    public ApiResponse<TestConnectionResponse> test(@PathVariable Long id) {
        return ApiResponse.ok(service.test(id));
    }

    @PostMapping("/{id}/sync")
    public ApiResponse<List<TableResponse>> sync(@PathVariable Long id) {
        return ApiResponse.ok(service.sync(id));
    }

    @GetMapping("/{id}/tables")
    public ApiResponse<List<TableResponse>> tables(@PathVariable Long id) {
        return ApiResponse.ok(service.tables(id));
    }
}

