package com.smartdataquerying.repository;

import com.smartdataquerying.model.DatasourceTable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DatasourceTableRepository extends JpaRepository<DatasourceTable, Long> {
    @EntityGraph(attributePaths = "columns")
    List<DatasourceTable> findByDatasourceIdOrderBySchemaNameAscTableNameAsc(Long datasourceId);
    List<DatasourceTable> findByDatasourceIdAndTableName(Long datasourceId, String tableName);
    void deleteByDatasourceId(Long datasourceId);
}
