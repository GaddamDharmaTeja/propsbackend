package com.prospr.dto;

import java.util.List;

public record ImportParseResponse(

        String templateId,

        String templateName,

        String bankName,

        int totalRows,

        List<ParsedTransaction> rows

) {
}