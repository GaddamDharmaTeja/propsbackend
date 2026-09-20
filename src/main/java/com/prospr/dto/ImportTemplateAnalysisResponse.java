package com.prospr.dto;

import java.util.List;
import java.util.Map;

public record ImportTemplateAnalysisResponse(
        List<String> columns,
        List<Map<String, String>> rows
) {
}