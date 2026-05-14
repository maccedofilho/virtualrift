package com.virtualrift.reports.model;

import org.springframework.http.MediaType;

public enum ReportExportFormat {
    JSON("json", MediaType.APPLICATION_JSON),
    HTML("html", MediaType.TEXT_HTML);

    private final String wireValue;
    private final MediaType mediaType;

    ReportExportFormat(String wireValue, MediaType mediaType) {
        this.wireValue = wireValue;
        this.mediaType = mediaType;
    }

    public String wireValue() {
        return wireValue;
    }

    public MediaType mediaType() {
        return mediaType;
    }

    public String fileExtension() {
        return wireValue;
    }

    public static ReportExportFormat fromWireValue(String value) {
        for (ReportExportFormat format : values()) {
            if (format.wireValue.equalsIgnoreCase(value)) {
                return format;
            }
        }

        throw new IllegalArgumentException("Unsupported report export format: " + value);
    }
}
