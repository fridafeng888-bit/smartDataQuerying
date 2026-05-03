package com.smartdataquerying.controller;

import com.smartdataquerying.common.ApiResponse;
import com.smartdataquerying.dto.DatasourceDtos.ColumnPatchRequest;
import com.smartdataquerying.dto.DatasourceDtos.ColumnResponse;
import com.smartdataquerying.dto.DatasourceDtos.TablePatchRequest;
import com.smartdataquerying.dto.DatasourceDtos.TableResponse;
import com.smartdataquerying.service.DatasourceService;
import org.springframework.web.bind.annotation.*;

@RestController
public class MetadataController {
    private final DatasourceService datasourceService;

    public MetadataController(DatasourceService datasourceService) {
        this.datasourceService = datasourceService;
    }

    @PatchMapping("/api/tables/{id}")
    public ApiResponse<TableResponse> patchTable(@PathVariable Long id, @RequestBody TablePatchRequest request) {
        return ApiResponse.ok(datasourceService.patchTable(id, request));
    }

    @PatchMapping("/api/columns/{id}")
    public ApiResponse<ColumnResponse> patchColumn(@PathVariable Long id, @RequestBody ColumnPatchRequest request) {
        return ApiResponse.ok(datasourceService.patchColumn(id, request));
    }
}

