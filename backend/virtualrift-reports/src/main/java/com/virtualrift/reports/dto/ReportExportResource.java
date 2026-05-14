package com.virtualrift.reports.dto;

import org.springframework.http.MediaType;

public record ReportExportResource(
        String fileName,
        MediaType contentType,
        byte[] content
) {
}
