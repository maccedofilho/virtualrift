package com.virtualrift.tenant.service;

import java.util.Map;

public interface RepositoryAccessValidator {

    RepositoryAccessValidationResult validateAccess(String repositoryTarget, Map<String, String> headers);
}
