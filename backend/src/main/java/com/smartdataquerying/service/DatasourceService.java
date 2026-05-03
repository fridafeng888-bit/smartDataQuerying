package com.smartdataquerying.service;

import com.smartdataquerying.common.AppException;
import com.smartdataquerying.dto.DatasourceDtos.*;
import com.smartdataquerying.model.*;
import com.smartdataquerying.repository.*;
import com.smartdataquerying.security.CryptoService;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

@Service
public class DatasourceService {
    private final DatasourceConfigRepository datasourceRepository;
    private final DatasourceTableRepository tableRepository;
    private final DatasourceColumnRepository columnRepository;
    private final CryptoService cryptoService;
    private final DatasourceConnectionFactory connectionFactory;

    public DatasourceService(DatasourceConfigRepository datasourceRepository,
                             DatasourceTableRepository tableRepository,
                             DatasourceColumnRepository columnRepository,
                             CryptoService cryptoService,
                             DatasourceConnectionFactory connectionFactory) {
        this.datasourceRepository = datasourceRepository;
        this.tableRepository = tableRepository;
        this.columnRepository = columnRepository;
        this.cryptoService = cryptoService;
        this.connectionFactory = connectionFactory;
    }

    public List<DatasourceResponse> list() {
        return datasourceRepository.findAll().stream().map(this::toResponse).toList();
    }

    public DatasourceResponse get(Long id) {
        return toResponse(find(id));
    }

    public DatasourceResponse create(DatasourceRequest request) {
        DatasourceConfig datasource = new DatasourceConfig();
        apply(datasource, request, true);
        return toResponse(datasourceRepository.save(datasource));
    }

    public DatasourceResponse update(Long id, DatasourceRequest request) {
        DatasourceConfig datasource = find(id);
        apply(datasource, request, false);
        return toResponse(datasourceRepository.save(datasource));
    }

    public void delete(Long id) {
        datasourceRepository.delete(find(id));
    }

    public TestConnectionResponse test(Long id) {
        DatasourceConfig datasource = find(id);
        try (Connection ignored = connectionFactory.open(datasource)) {
            return new TestConnectionResponse(true, "Connection successful");
        } catch (Exception ex) {
            return new TestConnectionResponse(false, ex.getMessage());
        }
    }

    @Transactional
    public List<TableResponse> sync(Long id) {
        DatasourceConfig datasource = find(id);
        tableRepository.deleteByDatasourceId(id);
        try (Connection connection = connectionFactory.open(datasource);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(metadataSql(datasource))) {
            DatasourceTable currentTable = null;
            String currentKey = "";
            while (rs.next()) {
                String schema = rs.getString("schema_name");
                String tableName = rs.getString("table_name");
                String key = schema + "." + tableName;
                if (!key.equals(currentKey)) {
                    currentKey = key;
                    currentTable = new DatasourceTable();
                    currentTable.datasource = datasource;
                    currentTable.schemaName = schema;
                    currentTable.tableName = tableName;
                    currentTable.commentText = rs.getString("table_comment");
                    currentTable.enabled = true;
                    tableRepository.save(currentTable);
                }
                DatasourceColumn column = new DatasourceColumn();
                column.table = currentTable;
                column.columnName = rs.getString("column_name");
                column.dataType = rs.getString("data_type");
                column.nullableCol = "YES".equalsIgnoreCase(rs.getString("is_nullable"));
                column.commentText = rs.getString("column_comment");
                column.ordinalPosition = rs.getInt("ordinal_position");
                column.enabled = true;
                columnRepository.save(column);
            }
            return tables(id);
        } catch (Exception ex) {
            throw new AppException(HttpStatus.BAD_REQUEST, "SYNC_FAILED", ex.getMessage());
        }
    }

    @Transactional
    public List<TableResponse> tables(Long datasourceId) {
        return tableRepository.findByDatasourceIdOrderBySchemaNameAscTableNameAsc(datasourceId)
                .stream().map(this::toTableResponse).toList();
    }

    public TableResponse patchTable(Long id, TablePatchRequest request) {
        DatasourceTable table = tableRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "TABLE_NOT_FOUND", "Table not found"));
        if (request.commentText() != null) table.commentText = request.commentText();
        if (request.enabled() != null) table.enabled = request.enabled();
        return toTableResponse(tableRepository.save(table));
    }

    public ColumnResponse patchColumn(Long id, ColumnPatchRequest request) {
        DatasourceColumn column = columnRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "COLUMN_NOT_FOUND", "Column not found"));
        if (request.commentText() != null) column.commentText = request.commentText();
        if (request.sensitive() != null) column.sensitive = request.sensitive();
        if (request.enabled() != null) column.enabled = request.enabled();
        return toColumnResponse(columnRepository.save(column));
    }

    public DatasourceConfig find(Long id) {
        return datasourceRepository.findById(id)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "DATASOURCE_NOT_FOUND", "Datasource not found"));
    }

    private void apply(DatasourceConfig datasource, DatasourceRequest request, boolean create) {
        datasource.name = request.name();
        datasource.type = request.type();
        datasource.host = request.host();
        datasource.port = request.port();
        datasource.databaseName = request.databaseName();
        datasource.username = request.username();
        datasource.sslEnabled = request.sslEnabled();
        datasource.enabled = request.enabled();
        if (create || (request.password() != null && !request.password().isBlank())) {
            datasource.encryptedPassword = cryptoService.encrypt(request.password());
        }
    }

    private String metadataSql(DatasourceConfig datasource) {
        if (datasource.type == DatasourceType.MYSQL) {
            return """
                    select table_schema as schema_name, table_name, coalesce(table_comment, '') as table_comment,
                           column_name, data_type, is_nullable, coalesce(column_comment, '') as column_comment, ordinal_position
                    from information_schema.columns
                    join information_schema.tables using (table_schema, table_name)
                    where table_schema = database()
                    order by table_schema, table_name, ordinal_position
                    """;
        }
        return """
                select c.table_schema as schema_name, c.table_name, coalesce(obj_description((quote_ident(c.table_schema)||'.'||quote_ident(c.table_name))::regclass), '') as table_comment,
                       c.column_name, c.data_type, c.is_nullable, coalesce(col_description((quote_ident(c.table_schema)||'.'||quote_ident(c.table_name))::regclass, c.ordinal_position), '') as column_comment, c.ordinal_position
                from information_schema.columns c
                where c.table_schema not in ('pg_catalog', 'information_schema')
                order by c.table_schema, c.table_name, c.ordinal_position
                """;
    }

    private DatasourceResponse toResponse(DatasourceConfig d) {
        return new DatasourceResponse(d.id, d.name, d.type, d.host, d.port, d.databaseName, d.username, d.sslEnabled, d.enabled);
    }

    private TableResponse toTableResponse(DatasourceTable table) {
        return new TableResponse(table.id, table.schemaName, table.tableName, table.commentText, table.enabled,
                table.columns.stream().map(this::toColumnResponse).toList());
    }

    private ColumnResponse toColumnResponse(DatasourceColumn column) {
        return new ColumnResponse(column.id, column.columnName, column.dataType, column.nullableCol,
                column.commentText, column.sensitive, column.enabled, column.ordinalPosition);
    }
}

