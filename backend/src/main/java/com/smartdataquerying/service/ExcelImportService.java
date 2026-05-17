package com.smartdataquerying.service;

import com.smartdataquerying.common.AppException;
import com.smartdataquerying.config.AppProperties;
import com.smartdataquerying.dto.ExcelDtos.ExcelImportResponse;
import com.smartdataquerying.dto.ExcelDtos.ImportedColumn;
import com.smartdataquerying.model.*;
import com.smartdataquerying.repository.*;
import com.smartdataquerying.service.ExcelTableWriter.ExcelColumn;
import com.smartdataquerying.service.ExcelTypeInferenceService.CellValue;
import com.smartdataquerying.service.ExcelTypeInferenceService.ExcelColumnType;
import jakarta.transaction.Transactional;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ExcelImportService {
    private static final String EXCEL_DATASOURCE_NAME = "Excel Imports";

    private final AppProperties properties;
    private final ExcelTypeInferenceService typeInferenceService;
    private final ExcelTableWriter tableWriter;
    private final DatasourceConfigRepository datasourceRepository;
    private final DatasourceTableRepository tableRepository;
    private final DatasourceColumnRepository columnRepository;
    private final ExcelImportJobRepository jobRepository;

    public ExcelImportService(AppProperties properties,
                              ExcelTypeInferenceService typeInferenceService,
                              ExcelTableWriter tableWriter,
                              DatasourceConfigRepository datasourceRepository,
                              DatasourceTableRepository tableRepository,
                              DatasourceColumnRepository columnRepository,
                              ExcelImportJobRepository jobRepository) {
        this.properties = properties;
        this.typeInferenceService = typeInferenceService;
        this.tableWriter = tableWriter;
        this.datasourceRepository = datasourceRepository;
        this.tableRepository = tableRepository;
        this.columnRepository = columnRepository;
        this.jobRepository = jobRepository;
    }

    @Transactional
    public ExcelImportResponse importFile(MultipartFile file, String displayName) {
        validateFile(file);
        String physicalTable = "imp_" + DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
                + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String finalDisplayName = normalizeDisplayName(displayName, file.getOriginalFilename());

        ExcelImportJob job = new ExcelImportJob();
        job.originalFileName = file.getOriginalFilename() == null ? "upload.xlsx" : file.getOriginalFilename();
        job.physicalTableName = physicalTable;
        job.displayTableName = finalDisplayName;
        job.status = "RUNNING";

        try {
            ParsedExcel parsed = parse(file);
            DatasourceConfig datasource = ensureExcelDatasource();
            job.datasource = datasource;
            job.rowCount = parsed.rows().size();
            job.columnCount = parsed.columns().size();
            job = jobRepository.save(job);

            tableWriter.createAndInsert(physicalTable, parsed.columns(), parsed.rows());
            DatasourceTable table = registerMetadata(datasource, physicalTable, finalDisplayName, parsed.columns());
            job.table = table;
            job.status = "SUCCESS";
            job = jobRepository.save(job);
            return toResponse(job, parsed.columns());
        } catch (AppException ex) {
            tableWriter.dropQuietly(physicalTable);
            saveFailedJobIfPossible(job, ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            tableWriter.dropQuietly(physicalTable);
            saveFailedJobIfPossible(job, ex.getMessage());
            throw new AppException(HttpStatus.BAD_REQUEST, "EXCEL_IMPORT_FAILED", ex.getMessage());
        }
    }

    public List<ExcelImportResponse> list() {
        return jobRepository.findTop100ByOrderByCreatedAtDesc().stream()
                .map(job -> toResponse(job, List.of()))
                .toList();
    }

    @Transactional
    public void delete(Long id) {
        ExcelImportJob job = jobRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "EXCEL_IMPORT_NOT_FOUND", "Excel import not found"));
        tableWriter.dropQuietly(job.physicalTableName);
        if (job.table != null) {
            tableRepository.delete(job.table);
        } else if (job.datasource != null) {
            tableRepository.findByDatasourceIdAndTableName(job.datasource.id, job.physicalTableName)
                    .forEach(tableRepository::delete);
        }
        jobRepository.delete(job);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "EXCEL_EMPTY", "Excel file is empty");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!filename.endsWith(".xlsx")) {
            throw new AppException(HttpStatus.BAD_REQUEST, "EXCEL_TYPE_UNSUPPORTED", "Only .xlsx files are supported");
        }
    }

    private ParsedExcel parse(MultipartFile file) throws Exception {
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new AppException(HttpStatus.BAD_REQUEST, "EXCEL_NO_SHEET", "Excel file has no sheets");
            }
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new AppException(HttpStatus.BAD_REQUEST, "EXCEL_HEADER_EMPTY", "Header row is empty");
            }
            List<String> headers = normalizeHeaders(headerRow, formatter);
            List<List<CellValue>> rows = new ArrayList<>();
            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                if (rows.size() >= properties.excel().maxRows()) {
                    throw new AppException(HttpStatus.BAD_REQUEST, "EXCEL_TOO_MANY_ROWS",
                            "Excel row count exceeds " + properties.excel().maxRows());
                }
                Row row = sheet.getRow(rowIndex);
                List<CellValue> values = readRow(row, headers.size(), formatter);
                if (values.stream().allMatch(CellValue::blank)) {
                    continue;
                }
                rows.add(values);
            }
            List<ExcelColumn> columns = inferColumns(headers, rows);
            return new ParsedExcel(columns, rows);
        }
    }

    private List<String> normalizeHeaders(Row headerRow, DataFormatter formatter) {
        int last = headerRow.getLastCellNum();
        if (last <= 0) {
            throw new AppException(HttpStatus.BAD_REQUEST, "EXCEL_HEADER_EMPTY", "Header row is empty");
        }
        Map<String, Integer> seen = new HashMap<>();
        List<String> headers = new ArrayList<>();
        for (int i = 0; i < last; i++) {
            String header = formatter.formatCellValue(headerRow.getCell(i)).trim();
            if (header.isBlank()) {
                throw new AppException(HttpStatus.BAD_REQUEST, "EXCEL_HEADER_EMPTY", "Header row contains empty cells");
            }
            int count = seen.getOrDefault(header, 0) + 1;
            seen.put(header, count);
            headers.add(count == 1 ? header : header + "_" + count);
        }
        return headers;
    }

    private List<CellValue> readRow(Row row, int columns, DataFormatter formatter) {
        List<CellValue> values = new ArrayList<>();
        for (int i = 0; i < columns; i++) {
            Cell cell = row == null ? null : row.getCell(i);
            String text = cell == null ? "" : formatter.formatCellValue(cell).trim();
            Double numeric = null;
            boolean dateCell = false;
            if (cell != null && cell.getCellType() == CellType.NUMERIC) {
                numeric = cell.getNumericCellValue();
                dateCell = DateUtil.isCellDateFormatted(cell);
            }
            values.add(new CellValue(cell, text, numeric, dateCell));
        }
        return values;
    }

    private List<ExcelColumn> inferColumns(List<String> headers, List<List<CellValue>> rows) {
        List<ExcelColumn> columns = new ArrayList<>();
        Map<String, Integer> safeNameCounts = new HashMap<>();
        int sampleRows = Math.min(rows.size(), properties.excel().sampleRows());
        for (int i = 0; i < headers.size(); i++) {
            int columnIndex = i;
            List<CellValue> samples = rows.stream()
                    .limit(sampleRows)
                    .map(row -> columnIndex < row.size() ? row.get(columnIndex) : new CellValue(null, "", null, false))
                    .toList();
            ExcelColumnType type = typeInferenceService.infer(samples);
            String safeName = uniqueSafeColumnName(toSafeColumnName(headers.get(i), i), safeNameCounts);
            columns.add(new ExcelColumn(safeName, headers.get(i), type));
        }
        return columns;
    }

    private String uniqueSafeColumnName(String candidate, Map<String, Integer> counts) {
        String key = candidate.toLowerCase(Locale.ROOT);
        int count = counts.getOrDefault(key, 0) + 1;
        counts.put(key, count);
        if (count == 1) {
            return candidate;
        }
        String suffix = "_" + count;
        int maxBaseLength = Math.max(1, 64 - suffix.length());
        String base = candidate.length() > maxBaseLength ? candidate.substring(0, maxBaseLength) : candidate;
        return base + suffix;
    }

    private String toSafeColumnName(String header, int index) {
        String normalized = header.trim().replace("`", "");
        if (normalized.isBlank()) {
            normalized = "col_" + String.format("%03d", index + 1);
        }
        if (normalized.length() > 58) {
            normalized = normalized.substring(0, 58);
        }
        return normalized;
    }

    private DatasourceConfig ensureExcelDatasource() {
        return datasourceRepository.findAll().stream()
                .filter(datasource -> datasource.type == DatasourceType.EXCEL_IMPORT)
                .findFirst()
                .orElseGet(() -> {
                    DatasourceConfig datasource = new DatasourceConfig();
                    datasource.name = EXCEL_DATASOURCE_NAME;
                    datasource.type = DatasourceType.EXCEL_IMPORT;
                    datasource.host = "system";
                    datasource.port = 0;
                    datasource.databaseName = "system";
                    datasource.username = "system";
                    datasource.encryptedPassword = "";
                    datasource.enabled = true;
                    return datasourceRepository.save(datasource);
                });
    }

    private DatasourceTable registerMetadata(DatasourceConfig datasource, String physicalTable,
                                             String displayName, List<ExcelColumn> columns) {
        DatasourceTable table = new DatasourceTable();
        table.datasource = datasource;
        table.schemaName = null;
        table.tableName = physicalTable;
        table.commentText = displayName;
        table.enabled = true;
        table = tableRepository.save(table);
        for (int i = 0; i < columns.size(); i++) {
            ExcelColumn imported = columns.get(i);
            DatasourceColumn column = new DatasourceColumn();
            column.table = table;
            column.columnName = imported.physicalName();
            column.dataType = imported.type().sqlType();
            column.commentText = imported.originalHeader();
            column.nullableCol = true;
            column.sensitive = false;
            column.enabled = true;
            column.ordinalPosition = i + 1;
            columnRepository.save(column);
        }
        return tableRepository.findById(table.id).orElse(table);
    }

    private ExcelImportResponse toResponse(ExcelImportJob job, List<ExcelColumn> columns) {
        return new ExcelImportResponse(
                job.id,
                job.datasource == null ? null : job.datasource.id,
                job.table == null ? null : job.table.id,
                job.originalFileName,
                job.physicalTableName,
                job.displayTableName,
                job.rowCount,
                job.columnCount,
                job.status,
                job.errorMessage,
                job.createdAt,
                columns.stream()
                        .map(column -> new ImportedColumn(column.physicalName(), column.originalHeader(), column.type().sqlType()))
                        .toList()
        );
    }

    private String normalizeDisplayName(String displayName, String filename) {
        if (displayName != null && !displayName.isBlank()) {
            return displayName.trim();
        }
        if (filename == null || filename.isBlank()) {
            return "Excel Import";
        }
        return filename.replaceFirst("(?i)\\.xlsx$", "");
    }

    private void saveFailedJobIfPossible(ExcelImportJob job, String message) {
        if (job.datasource == null) {
            return;
        }
        job.status = "FAILED";
        job.errorMessage = message;
        jobRepository.save(job);
    }

    private record ParsedExcel(List<ExcelColumn> columns, List<List<CellValue>> rows) {
    }
}
