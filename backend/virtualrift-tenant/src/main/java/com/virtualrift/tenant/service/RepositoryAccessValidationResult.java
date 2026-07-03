package com.virtualrift.tenant.service;

public record RepositoryAccessValidationResult(boolean accessible, String detail) {

    public static RepositoryAccessValidationResult success() {
        return new RepositoryAccessValidationResult(true, null);
    }

    public static RepositoryAccessValidationResult failure(String detail) {
        return new RepositoryAccessValidationResult(false, detail);
    }
}
