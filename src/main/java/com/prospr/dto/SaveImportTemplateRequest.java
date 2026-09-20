package com.prospr.dto;

import com.prospr.model.ImportTemplate;

import java.util.List;

public record SaveImportTemplateRequest(

        String name,

        String bankName,

        String fileType,

        ImportTemplate.TableDefinition tableDefinition,

        List<ImportTemplate.TemplateField> fields,

        ImportTemplate.ParsingRules parsingRules

) {
}