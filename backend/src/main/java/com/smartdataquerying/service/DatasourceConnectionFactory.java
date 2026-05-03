package com.smartdataquerying.service;

import com.smartdataquerying.model.DatasourceConfig;
import com.smartdataquerying.model.DatasourceType;
import com.smartdataquerying.security.CryptoService;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;

@Component
public class DatasourceConnectionFactory {
    private final CryptoService cryptoService;

    public DatasourceConnectionFactory(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    public Connection open(DatasourceConfig datasource) throws Exception {
        String password = cryptoService.decrypt(datasource.encryptedPassword);
        return DriverManager.getConnection(jdbcUrl(datasource), datasource.username, password);
    }

    public String jdbcUrl(DatasourceConfig datasource) {
        if (datasource.type == DatasourceType.MYSQL) {
            String ssl = datasource.sslEnabled ? "true" : "false";
            return "jdbc:mysql://" + datasource.host + ":" + datasource.port + "/" + datasource.databaseName
                    + "?useSSL=" + ssl + "&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        }
        return "jdbc:postgresql://" + datasource.host + ":" + datasource.port + "/" + datasource.databaseName
                + (datasource.sslEnabled ? "?ssl=true" : "");
    }
}

