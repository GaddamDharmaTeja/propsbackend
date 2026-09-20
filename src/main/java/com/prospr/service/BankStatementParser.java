package com.prospr.service;

import com.prospr.dto.ParsedTransaction;
import com.prospr.model.ImportTemplate;

import java.io.InputStream;
import java.util.List;

public interface BankStatementParser {

    List<ParsedTransaction> parse(
            InputStream inputStream,
            String password,
            ImportTemplate template
    ) throws Exception;
}