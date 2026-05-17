package com.smartdataquerying.controller;

import com.smartdataquerying.common.ApiResponse;
import com.smartdataquerying.dto.ExcelDtos.ExcelImportResponse;
import com.smartdataquerying.service.ExcelImportService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import java.util.List;

@RestController
@RequestMapping("/api/excel")
public class ExcelImportController {
    private final ExcelImportService service;

    public ExcelImportController(ExcelImportService service) {
        this.service = service;
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ExcelImportResponse> importExcel(@RequestPart("file") MultipartFile file,
                                                        @RequestParam(value = "displayName", required = false) String displayName) {
        return ApiResponse.ok(service.importFile(file, displayName));
    }

    @GetMapping("/imports")
    public ApiResponse<List<ExcelImportResponse>> imports() {
        return ApiResponse.ok(service.list());
    }

    @DeleteMapping("/imports/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok(null);
    }
}
