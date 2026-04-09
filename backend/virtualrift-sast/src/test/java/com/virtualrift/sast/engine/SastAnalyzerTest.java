package com.virtualrift.sast.engine;

import com.virtualrift.common.model.Severity;
import com.virtualrift.common.model.VulnerabilityFinding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SastAnalyzer Tests")
class SastAnalyzerTest {

    private final SastAnalyzer analyzer = new SastAnalyzer();

    @Test
    @DisplayName("should reject null code input")
    void analyzeCode_quandoCodigoNulo_lancaExcecao() {
        assertThrows(IllegalArgumentException.class, () -> analyzer.analyzeCode(null, "Example.java"));
    }

    @Test
    @DisplayName("should detect hardcoded password in Java code")
    void analyzeCode_quandoSenhaHardcoded_retornaFindingCritico() {
        String code = """
                public class Example {
                    void run() {
                        String password = "SuperSecret123";
                    }
                }
                """;

        List<VulnerabilityFinding> findings = analyzer.analyzeCode(code, "Example.java");

        assertTrue(findings.stream().anyMatch(f ->
                f.title().equals("Hardcoded Password") && f.severity() == Severity.CRITICAL
        ));
    }

    @Test
    @DisplayName("should detect SQL injection pattern in Java code")
    void analyzeCode_quandoSqlComConcatenacao_retornaFindingHigh() {
        String code = """
                class Example {
                    void run(java.sql.Statement stmt, String userId) throws Exception {
                        stmt.execute("SELECT * FROM users WHERE id = " + userId);
                    }
                }
                """;

        List<VulnerabilityFinding> findings = analyzer.analyzeCode(code, "Example.java");

        assertTrue(findings.stream().anyMatch(f ->
                f.title().equals("Potential SQL Injection") && f.severity() == Severity.HIGH
        ));
    }

    @Test
    @DisplayName("should detect command injection pattern in Java code")
    void analyzeCode_quandoExecucaoDeComando_retornaFindingHigh() {
        String code = """
                class Example {
                    void run(String cmd) throws Exception {
                        Runtime.getRuntime().exec(cmd);
                    }
                }
                """;

        List<VulnerabilityFinding> findings = analyzer.analyzeCode(code, "Example.java");

        assertTrue(findings.stream().anyMatch(f ->
                f.title().equals("Potential Command Injection") && f.severity() == Severity.HIGH
        ));
    }

    @Test
    @DisplayName("should detect debug statement in JavaScript code")
    void analyzeCode_quandoConsoleLog_retornaFindingLow() {
        String code = """
                export function debugUser(user) {
                  console.log(user);
                }
                """;

        List<VulnerabilityFinding> findings = analyzer.analyzeCode(code, "debug.js");

        assertTrue(findings.stream().anyMatch(f ->
                f.title().equals("Debug Statement") && f.severity() == Severity.LOW
        ));
    }

    @Test
    @DisplayName("should return no findings for safe code")
    void analyzeCode_quandoCodigoSeguro_retornaVazio() {
        String code = """
                class SafeCode {
                    String hash(String input) {
                        return input == null ? "" : input.trim();
                    }
                }
                """;

        assertTrue(analyzer.analyzeCode(code, "SafeCode.java").isEmpty());
    }

    @Test
    @DisplayName("should analyze file contents from disk")
    void analyzeFile_quandoArquivoContemSecret_retornaFinding() throws IOException {
        Path file = Files.createTempFile("sast-secret", ".java");
        Files.writeString(file, """
                class SecretFile {
                    String api_key = "abcdefghijklmnopqrstuv";
                }
                """);

        List<VulnerabilityFinding> findings = analyzer.analyzeFile(file);

        assertTrue(findings.stream().anyMatch(f ->
                f.title().equals("Hardcoded API Key") && f.location().contains(file.toString())
        ));
    }

    @Test
    @DisplayName("should reject invalid directory path")
    void analyzeDirectory_quandoDiretorioInvalido_lancaExcecao() {
        assertThrows(IllegalArgumentException.class, () -> analyzer.analyzeDirectory(Path.of("/path/that/does/not/exist")));
    }

    @Test
    @DisplayName("should aggregate findings from supported files in a directory")
    void analyzeDirectory_quandoHaArquivosSuportados_agregaFindings() throws IOException {
        Path directory = Files.createTempDirectory("sast-project");
        Path javaFile = directory.resolve("Example.java");
        Path ignoredFile = directory.resolve("notes.txt");

        Files.writeString(javaFile, """
                class Example {
                    void run() {
                        String password = "SuperSecret123";
                    }
                }
                """);
        Files.writeString(ignoredFile, "password = ignored");

        List<VulnerabilityFinding> findings = analyzer.analyzeDirectory(directory);

        assertFalse(findings.isEmpty());
        assertEquals(1, findings.size());
        assertTrue(findings.get(0).location().contains("Example.java"));
    }
}
