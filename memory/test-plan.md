# Plano de Testes - VirtualRift (TDD)

## Ordem de Implementação (dependências)

```
1. virtualrift-common (base para todos)
2. virtualrift-tenant (multitenancy base)
3. virtualrift-auth (JWT + Redis)
4. virtualrift-orchestrator (Kafka + events)
5. virtualrift-web-scanner
6. virtualrift-api-scanner
7. virtualrift-network-scanner
8. virtualrift-sast
9. virtualrift-reports
10. virtualrift-gateway (último - depende de todos)
11. Frontend (types, api-client, ui, dashboard)
```

---

## 1. virtualrift-common

### Unit Tests
- [ ] **TenantIdTest** — validação de UUID
- [ ] **UserIdTest** — validação de UUID
- [ ] **EmailTest** — formato válido, inválido
- [ ] **ScanIdTest** — validação de UUID
- [ ] **SeverityTest** — enum values, fromString
- [ ] **ScanStatusTest** — transições válidas (PENDING → RUNNING → COMPLETED/FAILED)
- [ ] **VulnerabilityFindingTest** — validação de campos obrigatórios
- [ ] **TenantQuotaTest** — validação de limites
- [ ] **ScanRequestTest** — validação de target URL (blocklist)
- [ ] **ScanResultTest** — agregação de findings

### Integration Tests
- [ ] **SerializationTest** — JSON serialize/deserialize
- [ ] **EventSerializationTest** — Kafka events

---

## 2. virtualrift-tenant

### Unit Tests
- [ ] **TenantServiceTest**
  - [ ] createTenant_quandoDadosValidos_criaTenant
  - [ ] createTenant_quandoSlugDuplicado_lancaExcecao
  - [ ] getTenantById_quandoNaoExiste_retornaEmpty
  - [ ] updatePlan_quandoValido_atualizaQuotas
  - [ ] activateTenant_quandoSuspended_reativa
  - [ ] suspendTenant_quandoAtivo_suspende

- [ ] **TenantQuotaServiceTest**
  - [ ] canStartScan_quandoAbaixoDoLimite_retornaTrue
  - [ ] canStartScan_quandoNoLimite_lancaQuotaExcededException
  - [ ] incrementScanCount_quandoLimiteNaoAtingido_incrementa
  - [ ] resetDailyCount_quandoMeiaNoite_reseta

- [ ] **TenantRepositoryTest**
  - [ ] findById_quandoTenantExiste_retornaTenant
  - [ ] findBySlug_quandoSlugValido_retornaTenant

### Integration Tests
- [ ] **TenantIntegrationTest**
  - [ ] createTenant_comDadosCompletos_persisteComSucesso
  - [ ] createTenant_quandoSlugDuplicado_falhaUnicidade
  - [ ] updateTenant_quandoTenantExiste_atualizaCampos
  - [ ] deleteTenant_quandoTemScans_lancaExcecao

- [ ] **TenantIsolationTest** (CRÍTICO)
  - [ ] queryTenantA_quandoAutenticadoComoTenantB_retornaVazio
  - [ ] queryTenantA_quandoAutenticadoComoTenantA_retornaDados
  - [ ] crossTenantQuery_quandoForzado_tentaAcessoNegado

---

## 3. virtualrift-auth

### Unit Tests
- [ ] **JwtServiceTest**
  - [ ] generateToken_quandoUserValido_retornaJwt
  - [ ] generateToken_contemTenantIdEUserIdERoles
  - [ ] validateToken_quandoValido_retornaClaims
  - [ ] validateToken_quandoExpirado_lancaExcecao
  - [ ] validateToken_quandoAssinaturaInvalida_lancaExcecao
  - [ ] validateToken_quantoMalFormado_lancaExcecao

- [ ] **RefreshTokenServiceTest**
  - [ ] generateRefreshToken_quandoUserValido_retornaToken
  - [ ] validateRefreshToken_quandoValido_retornaUserId
  - [ ] revokeRefreshToken_quandoExecutado_adicionaNaDenylist

- [ ] **TokenDenylistServiceTest**
  - [ ] isDenied_quantoTokenNaLista_retornaTrue
  - [ ] isDenied_quantoTokenNãoNaLista_retornaFalse
  - [ ] add_quantoAdicionado_expiraAposTempoConfigurado

- [ ] **LoginServiceTest**
  - [ ] login_quantoCredenciaisValidas_retornaTokens
  - [ ] login_quantoEmailNaoExiste_lancaAuthenticationException
  - [ ] login_quantoSenhaIncorreta_lancaAuthenticationException
  - [ ] login_quantoUsuarioPendenteVerificacao_lancaExcecao

- [ ] **PasswordServiceTest**
  - [ ] hashPassword_quantoSenhaValida_retornaHash
  - [ ] verifyPassword_quantoSenhaCorreta_retornaTrue
  - [ ] verifyPassword_quantoSenhaIncorreta_retornaFalse

### Integration Tests
- [ ] **AuthIntegrationTest**
  - [ ] login_comCredenciaisValidas_retorna200
  - [ ] login_comCredenciaisInvalidas_retorna401
  - [ ] refreshToken_comTokenValido_retornaNovoAccessToken
  - [ ] logout_comTokenValido_adicionaNaDenylist
  - [ ] logout_comTokenRevogado_retorna401

- [ ] **JwtSecurityTest**
  - [ ] endpointSemToken_quandoChamado_retorna401
  - [ ] endpointComTokenValido_quandoChamado_retorna200
  - [ ] endpointComTokenExpirado_quandoChamado_retorna401
  - [ ] endpointComTokenOutroTenant_quandoChamado_retorna403

---

## 4. virtualrift-orchestrator

### Unit Tests
- [ ] **ScanOrchestratorServiceTest**
  - [ ] requestScan_quandoRequestValido_publicaEventoScanRequested
  - [ ] requestScan_quandoQuotaExcedida_lancaQuotaExcededException
  - [ ] requestScan_quandoTargetNaoAutorizado_lancaExcecao
  - [ ] requestScan_quandoSucesso_retornaScanIdEPendingStatus

- [ ] **ScanStatusServiceTest**
  - [ ] updateStatus_quandoTransicaoValida_atualizaStatus
  - [ ] updateStatus_quandoTransicaoInvalida_lancaExcecao
  - [ ] getStatus_quandoScanExiste_retornaStatus

- [ ] **KafkaEventPublisherTest**
  - [ ] publishScanRequested_quandoChamado_publicaNoTopico
  - [ ] publishScanRequested_quandoFalha_lancaExcecao
  - [ ] publishScanCompleted_quandoChamado_publicaNoTopico

- [ ] **ScanRequestValidatorTest**
  - [ ] validate_quandoTargetNaoInformado_lancaValidationException
  - [ ] validate_quandoTargetNaBlocklist_lancaSecurityException
  - [ ] validate_quandoTargetNaoHttpHttps_lancaValidationException
  - [ ] validate_quandoTargetIpInterno_lancaSecurityException

### Integration Tests
- [ ] **OrchestratorIntegrationTest**
  - [ ] requestScan_comRequestValido_criaRegistroEPublicaEvento
  - [ ] requestScan_comTargetBloqueado_rejeita
  - [ ] consumeScanCompleted_quandoRecebido_atualizaStatusEFindings

- [ ] **KafkaIntegrationTest**
  - [ ] publishScanRequested_quandoPublicado_consomeComSucesso
  - [ ] consumeScanRequested_quandoRecebido_iniciaScanner
  - [ ] publishScanCompleted_quandoPublicado_consomeComSucesso

---

## 5. virtualrift-web-scanner

### Unit Tests
- [ ] **WebScanEngineTest**
  - [ ] detectXss_quandoVulneravel_retornaHighFinding
  - [ ] detectXss_quandoProtegido_retornaNoFindings
  - [ ] detectSqlInjection_quandoVulneravel_retornaCriticalFinding
  - [ ] detectSqlInjection_quandoProtegido_retornaNoFindings
  - [ ] detectCsrf_quandoTokenAusente_retornaMediumFinding
  - [ ] detectCsrf_quandoTokenPresente_retornaNoFindings
  - [ ] detectMissingSecurityHeaders_quandoAusentes_retornaLowFinding
  - [ ] detectOpenRedirect_quandoVulneravel_retornaMediumFinding
  - [ ] detectOpenRedirect_quandoProtegido_retornaNoFindings

- [ ] **CrawlServiceTest**
  - [ ] crawl_quandoDepthZero_retornaApenasPaginaInicial
  - [ ] crawl_quandoDepthUm_retornaPaginaInicialELinks
  - [ ] crawl_quandoRobotsTxtDisallowed_respeitaRestricoes
  - [ ] crawl_quandoLoopDetectado_evitaCicloInfinito

- [ ] **XssDetectorTest**
  - [ ] detectReflectedXss_quandoRefletido_retornaFinding
  - [ ] detectStoredXss_quandoPersistido_retornaFinding
  - [ ] detectDomBasedXss_quandoVulneravel_retornaFinding

- [ ] **SqlInjectionDetectorTest**
  - [ ] detectErrorBasedSqlInjection_quandoVulneravel_retornaFinding
  - [ ] detectUnionBasedSqlInjection_quandoVulneravel_retornaFinding
  - [ ] detectBooleanBasedSqlInjection_quandoVulneravel_retornaFinding

### Integration Tests
- [ ] **WebScannerIntegrationTest**
  - [ ] scan_quandoTargetDvwa_detectaVulnerabilidadesConhecidas
  - [ ] scan_quandoTargetLimpo_retornaZeroFindings
  - [ ] scan_quandoTimeout_existeTimeoutEContinua
  - [ ] scan_quandoTargetIpInterno_rejeitaComErro

- [ ] **PlaywrightIntegrationTest**
  - [ ] renderPage_quandoJavaScriptExecuta_retornaHtmlCompleto
  - [ ] renderPage_quandoTimeout_lancaExcecao
  - [ ] executeJavaScript_quandoScriptInjetado_retornaResultado

---

## 6. virtualrift-api-scanner

### Unit Tests
- [ ] **ApiScanEngineTest**
  - [ ] scanOpenApiSpec_quandoSpecValida_analisaEndpoints
  - [ ] scanOpenApiSpec_quandoSpecInvalida_lancaExcecao
  - [ ] detectBrokenAuth_quandoSemAutenticacao_retornaHighFinding
  - [ ] detectExcessiveDataExposure_quandoCampoSensivel_retornaMediumFinding
  - [ ] detectRateLimiting_quandoAusente_retornaLowFinding
  - [ ] detectImproperErrorHandling_quandoStacktrace_retornaLowFinding

- [ ] **OpenApiParserTest**
  - [ ] parse_quandoOpenApi3_retornaEstrutura
  - [ ] parse_quandoGraphQL_retornaEstrutura
  - [ ] parse_quandoFormatoInvalido_lancaExcecao

- [ ] **FuzzingEngineTest**
  - [ ] fuzzEndpoint_quandoAutenticado_testaPayloadsMaliciosos
  - [ ] fuzzEndpoint_quandoSemAutenticacao_testaBypass
  - [ ] fuzzInput_quandoSqlInjection_testaVariacoes

### Integration Tests
- [ ] **ApiScannerIntegrationTest**
  - [ ] scan_quandoApiVulneravel_detectaFalhas
  - [ ] scan_quandoApiSegura_retornaZeroFindings
  - [ ] scanFuzzing_quandoRateLimit_respeitaLimites

---

## 7. virtualrift-network-scanner

### Unit Tests
- [ ] **NetworkScanEngineTest**
  - [ ] scanPorts_quandoPortasAbertas_retornaFindings
  - [ ] scanPorts_quandoPortasFechadas_retornaNoFindings
  - [ ] detectTlsIssues_quandoCertificadoExpirado_retornaHighFinding
  - [ ] detectTlsIssues_quandoCipherFraco_retornaMediumFinding
  - [ ] detectTlsIssues_quandoVersaoAntiga_retornaMediumFinding
  - [ ] lookupCve_quandoVersaoConhecida_retornaCves

- [ ] **NmapWrapperTest**
  - [ ] execute_quandoComandoValido_retornaResultado
  - [ ] execute_quandoComandoInvalido_lancaExcecao
  - [ ] buildCommand_quandoOpcoesValidas_constroiComandoSeguro
  - [ ] buildCommand_quandoOpcaoInvalida_removeOpcaoPerigosa

- [ ] **TlsAnalyzerTest**
  - [ ] analyzeCertificate_quandoValido_retornaOk
  - [ ] analyzeCertificate_quandoExpirado_retornaErro
  - [ ] analyzeCertificate_quandoSelfSigned_retornaAviso
  - [ ] analyzeCiphers_quandoApenasSeguros_retornaOk
  - [ ] analyzeCiphers_quandoFracos_retornaFinding

### Integration Tests
- [ ] **NetworkScannerIntegrationTest**
  - [ ] scan_quandoAlvoComPortasAbertas_detectaPortas
  - [ ] scan_quandoAlvoComTlsFraco_detectaProblemas
  - [ ] scan_quandoIpBloqueado_rejeitaComErro
  - [ ] scan_quandoRangeExcedeLimite_lancaExcecao

---

## 8. virtualrift-sast

### Unit Tests
- [ ] **SastEngineTest**
  - [ ] scanRepository_quandoClona_repositorioTemporario
  - [ ] scanRepository_quandoScanCompleto_deletaRepositorio
  - [ ] detectHardcodedSecrets_quandoApiKeyPresente_retornaCriticalFinding
  - [ ] detectHardcodedSecrets_quandoSenhaPresente_retornaCriticalFinding
  - [ ] detectSqlInjection_quandoConcatenacao_retornaHighFinding
  - [ ] detectCommandInjection_quandoRuntimeExec_retornaCriticalFinding

- [ ] **SecretPatternRegistryTest**
  - [ ] getPatterns_quandoChamado_retornaTodasRegras
  - [ ] matchPattern_quandoSecretAchado_retornaMatch
  - [ ] matchPattern_quandoCodigoLimpo_retornaVazio

- [ ] **GitCloneServiceTest**
  - [ ] clone_quandoUrlValida_clonaRepositorio
  - [ ] clone_quandoUrlInvalida_lancaExcecao
  - [ ] cleanup_quandoExecutado_deletaRepositorio

### Integration Tests
- [ ] **SastIntegrationTest**
  - [ ] scan_quandoRepoComSecrets_detectaSegredos
  - [ ] scan_quandoRepoLimpo_retornaZeroFindings
  - [ ] scan_quandoRepoGravo_concluiDentroDoTimeout

---

## 9. virtualrift-reports

### Unit Tests
- [ ] **ReportServiceTest**
  - [ ] generateReport_quandoScansValidos_retornaRelatorio
  - [ ] generateReport_quandoScansVazios_retornaRelatorioVazio
  - [ ] aggregateFindings_quandoMultiplasSources_agrupaPorCategoria
  - [ ] prioritizeFindings_quandoVariadosSeverities_ordaPorPrioridade
  - [ ] maskSecrets_quandoFindingContemCredencial_mascara

- [ ] **PdfGeneratorTest**
  - [ ] generatePdf_quandoRelatorioValido_retornaBytes
  - [ ] generatePdf_quandoTemplateInvalido_lancaExcecao

- [ ] **ReportRepositoryTest**
  - [ ] save_quandoRelatorioNovo_persiste
  - [ ] findByTenantId_quandoTenantTemRelatorios_retornaLista
  - [ ] findByTenantId_quandoOutroTenant_retornaVazio

### Integration Tests
- [ ] **ReportIntegrationTest**
  - [ ] generateFullReport_quandoScansCompletos_geraPdf
  - [ ] generateExecutiveReport_quandoScansCompletos_geraResumo
  - [ ] tenantIsolation_quandoRelatorioTenantA_tenantBNaoAcessa

---

## 10. virtualrift-gateway

### Unit Tests
- [ ] **RouteConfigurationTest**
  - [ ] routes_quandoConfigurado_todasRotasDefinidas
  - [ ] authRoute_quandoChamado_direcionaParaAuthService
  - [ ] tenantRoute_quandoChamado_direcionaParaTenantService
  - [ ] scannerRoute_quandoChamado_direcionaParaOrchestrator

- [ ] **JwtValidationFilterTest**
  - [ ] filter_quandoTokenValido_adicionaHeaders
  - [ ] filter_quandoTokenAusente_retorna401
  - [ ] filter_quandoTokenInvalido_retorna401
  - [ ] filter_quandoPathPublico_permitePassagem

- [ ] **RateLimitFilterTest**
  - [ ] filter_quandoDentroDoLimite_permite
  - [ ] filter_quandoAcimaDoLimite_retorna429
  - [ ] filter_quandoTenantDiferente_contaSeparado

- [ ] **TenantContextFilterTest**
  - [ ] filter_quandoTokenValido_extraiTenantId
  - [ ] filter_quandoTokenSemTenantId_retorna400

### Integration Tests
- [ ] **GatewayIntegrationTest**
  - [ ] request_semToken_retorna401
  - [ ] request_comTokenValido_retorna200
  - [ ] request_comTokenTenantA_acessaSomenteTenantA
  - [ ] request_rateLimiting_quandoExcedido_retorna429

---

## 11. Frontend

### virtualrift-types
- [ ] **TenantTypesTest** — tipo exports, validação
- [ ] **ScanTypesTest** — enum values, type guards
- [ ] **VulnerabilityTypesTest** — severity mappings

### virtualrift-api-client
- [ ] **ApiClientTest** — base URL, interceptors
- [ ] **AuthClientTest** — login, refresh, logout
- [ ] **ScanClientTest** — list, get, create
- [ ] **TenantClientTest** — get, update
- [ ] **ReportClientTest** — generate, download

### virtualrift-ui
- [ ] **ButtonComponentTest** — variants, sizes, disabled
- [ ] **InputComponentTest** — validation, error states
- [ ] **ScanCardComponentTest** — props rendering
- [ ] **VulnerabilityBadgeTest** — severity colors

### virtualrift-dashboard
- [ ] **DashboardPageTest** — renders correctly
- [ ] **ScansListPageTest** — displays scans
- [ ] **ScanDetailPageTest** — shows findings
- [ ] **LoginPageTest** — form submission
- [ ] **TenantSettingsPageTest** — updates settings

---

## Resumo

| Fase | Módulo | Testes |
|------|--------|--------|
| 1 | common | ~15 tests |
| 2 | tenant | ~20 tests |
| 3 | auth | ~25 tests |
| 4 | orchestrator | ~15 tests |
| 5 | web-scanner | ~30 tests |
| 6 | api-scanner | ~20 tests |
| 7 | network-scanner | ~25 tests |
| 8 | sast | ~20 tests |
| 9 | reports | ~20 tests |
| 10 | gateway | ~20 tests |
| 11 | frontend | ~30 tests |
| **TOTAL** | | **~240 tests** |
