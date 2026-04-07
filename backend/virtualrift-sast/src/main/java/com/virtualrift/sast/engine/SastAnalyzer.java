package com.virtualrift.sast.engine;

import com.virtualrift.common.model.Severity;
import com.virtualrift.common.model.VulnerabilityFinding;
import com.virtualrift.common.model.TenantId;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public class SastAnalyzer {

    private enum Language {
        JAVA, PYTHON, JAVASCRIPT, TYPESCRIPT, GO, RUBY, PHP, CSHARP, RUST, CPP, UNKNOWN
    }

    private static final Map<Language, Set<String>> FILE_EXTENSIONS = Map.of(
            Language.JAVA, Set.of(".java"),
            Language.PYTHON, Set.of(".py", ".pyw"),
            Language.JAVASCRIPT, Set.of(".js", ".jsx", ".mjs"),
            Language.TYPESCRIPT, Set.of(".ts", ".tsx"),
            Language.GO, Set.of(".go"),
            Language.RUBY, Set.of(".rb"),
            Language.PHP, Set.of(".php"),
            Language.CSHARP, Set.of(".cs"),
            Language.RUST, Set.of(".rs"),
            Language.CPP, Set.of(".cpp", ".cc", ".cxx", ".hpp", ".h", ".c")
    );

    private static final Pattern HARDCODED_PASSWORD = Pattern.compile(
            "(?i)(password|pwd|pass|secret|passwd)\\s*[=:]\\s*[\"'`][^\"'`]{8,}[\"'`]",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern HARDCODED_API_KEY = Pattern.compile(
            "(?i)(api[_-]?key|apikey|access[_-]?token|secret[_-]?key|private[_-]?key|auth[_-]?token)\\s*[=:]\\s*[\"'`][a-zA-Z0-9\\-=_]{20,}[\"'`]"
    );

    private static final Pattern JWT_TOKEN = Pattern.compile(
            "eyJ[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+"
    );

    private static final Pattern AWS_KEY = Pattern.compile(
            "(?i)(AKIA[0-9A-Z]{16})"
    );

    private static final Pattern AWS_SECRET = Pattern.compile(
            "(?i)[0-9a-zA-Z/+]{40}"
    );

    private static final Pattern GENERIC_SECRET = Pattern.compile(
            "(?i)(sk_|pk_|secret_|api_|access_token)\\s*[=:]\\s*[\"'`]?[a-zA-Z0-9_\\-]{20,}"
    );

    private static final Pattern IP_ADDRESS = Pattern.compile(
            "\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"
    );

    private static final Pattern INTERNAL_URL = Pattern.compile(
            "(?i)(localhost|127\\.0\\.0\\.1|0\\.0\\.0\\.0|::1|192\\.168\\.|10\\.|172\\.1[6-9]\\.|172\\.2[0-9]\\.|172\\.3[01]\\.)"
    );

    private static final Pattern DEBUG_STATEMENT_JAVA = Pattern.compile(
            "System\\.(out|err)\\.print|printStackTrace\\(\\)|\\.printStackTrace\\(\\)"
    );

    private static final Pattern DEBUG_STATEMENT_PYTHON = Pattern.compile(
            "(?i)print\\s*\\(|pdb\\.set_trace|breakpoint\\(\\)|logging\\.debug"
    );

    private static final Pattern DEBUG_STATEMENT_JS = Pattern.compile(
            "(?i)console\\.(log|debug|warn|error|info)|debugger"
    );

    private static final Pattern SQL_INJECTION_JAVA = Pattern.compile(
            "(?i)(execute|query|createQuery|createNativeQuery)\\s*\\([^)]*\\+\\s*[^)]*\\)"
    );

    private static final Pattern SQL_INJECTION_PYTHON = Pattern.compile(
            "(?i)cursor\\.execute\\s*\\([^)]*%|\\.format\\(|\\.format\\(.*\\{\\}"
    );

    private static final Pattern SQL_INJECTION_JS = Pattern.compile(
            "(?i)(query|execute)\\s*\\([^)]*\\$\\{|\\`.*\\$\\{.*\\`"
    );

    private static final Pattern COMMAND_INJECTION_JAVA = Pattern.compile(
            "(?i)Runtime\\.getRuntime\\(\\)\\.exec|ProcessBuilder|new\\s+Process\\("
    );

    private static final Pattern COMMAND_INJECTION_PYTHON = Pattern.compile(
            "(?i)os\\.system|subprocess\\.(call|run|Popen)\\s*\\([^)]*shell=True|commands\\.getoutput"
    );

    private static final Pattern COMMAND_INJECTION_JS = Pattern.compile(
            "(?i)child_process\\.(exec|spawn|execSync)|require\\(['\"]child_process['\"]\\)"
    );

    private static final Pattern COMMAND_INJECTION_PHP = Pattern.compile(
            "(?i)shell_exec\\s*\\(|exec\\s*\\(|passthru\\s*\\(|system\\s*\\(|`[^`]*`"
    );

    private static final Pattern EVAL_JAVA = Pattern.compile(
            "(?i)(ScriptEngine|eval)\\s*\\("
    );

    private static final Pattern EVAL_PYTHON = Pattern.compile(
            "(?i)eval\\s*\\(|exec\\s*\\(|compile\\s*\\("
    );

    private static final Pattern EVAL_JS = Pattern.compile(
            "(?i)eval\\s*\\(|Function\\s*\\(|setTimeout\\s*\\(\\s*['\"]|setInterval\\s*\\(\\s*['\"]"
    );

    private static final Pattern EVAL_PHP = Pattern.compile(
            "(?i)eval\\s*\\(|assert\\s*\\(|create_function\\s*\\("
    );

    private static final Pattern WEAK_CRYPTO_JAVA = Pattern.compile(
            "(?i)Cipher\\s*\\(\\s*\"DES/|Cipher\\s*\\(\\s*\"MD5/|MessageDigest\\.getInstance\\([\"']?(MD5|SHA1|DES|RC4)"
    );

    private static final Pattern WEAK_CRYPTO_PYTHON = Pattern.compile(
            "(?i)hashlib\\.(md5|sha1)|Crypto\\.Cipher\\.(DES|ARC4|AES\\s*\\(\\s*[^,)]*\\s*,\\s*[^,)]*\\s*,\\s*[^,)]*\\s*\\))"
    );

    private static final Pattern WEAK_CRYPTO_JS = Pattern.compile(
            "(?i)(crypto\\.create(Cipher|Cipheriv|Decipher|Decipheriv)\\s*\\(['\"]?(des|rc4|rc2|md5|sha1)|bcrypt\\.hashSync\\s*\\(.*\\s*,\\s*\\s*\\d+\\s*\\))"
    );

    private static final Pattern WEAK_RANDOM_JAVA = Pattern.compile(
            "(?i)new\\s+Random\\(\\)|java\\.util\\.Random|SecureRandom\\.getInstance\\([\"']?SHA1PRNG"
    );

    private static final Pattern WEAK_RANDOM_PYTHON = Pattern.compile(
            "(?i)random\\.random|random\\.randint|random\\.choice"
    );

    private static final Pattern WEAK_RANDOM_JS = Pattern.compile(
            "(?i)Math\\.random|crypto\\.pseudoRandomBytes"
    );

    private static final Pattern HARDcoded_IP = Pattern.compile(
            "(?i)(host|server|url|endpoint|db_host|database_host|connection_string)\\s*[=:]\\s*[\"'][^\"']*\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"
    );

    public List<VulnerabilityFinding> analyzeFile(Path filePath) {
        if (filePath == null) {
            throw new IllegalArgumentException("File path cannot be null");
        }

        List<VulnerabilityFinding> findings = new ArrayList<>();

        try {
            String content = Files.readString(filePath);
            String fileName = filePath.getFileName().toString();
            Language language = detectLanguage(fileName);

            findings.addAll(scanForSecurityIssues(content, filePath.toString(), fileName, language));

        } catch (IOException e) {
        }

        return findings;
    }

    public List<VulnerabilityFinding> analyzeDirectory(Path directoryPath) {
        if (directoryPath == null || !Files.exists(directoryPath)) {
            throw new IllegalArgumentException("Directory path cannot be null and must exist");
        }

        List<VulnerabilityFinding> findings = new ArrayList<>();

        try {
            Files.walk(directoryPath)
                .filter(p -> isCodeFile(p))
                .forEach(p -> findings.addAll(analyzeFile(p)));

        } catch (IOException e) {
        }

        return findings;
    }

    public List<VulnerabilityFinding> analyzeCode(String code, String fileName) {
        if (code == null) {
            throw new IllegalArgumentException("Code cannot be null");
        }

        Language language = detectLanguage(fileName != null ? fileName : "");
        return scanForSecurityIssues(code, fileName != null ? fileName : "unknown", fileName, language);
    }

    private boolean isCodeFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return FILE_EXTENSIONS.values().stream()
                .flatMap(Set::stream)
                .anyMatch(fileName::endsWith);
    }

    private Language detectLanguage(String fileName) {
        String lower = fileName.toLowerCase();

        for (Map.Entry<Language, Set<String>> entry : FILE_EXTENSIONS.entrySet()) {
            for (String ext : entry.getValue()) {
                if (lower.endsWith(ext)) {
                    return entry.getKey();
                }
            }
        }

        return Language.UNKNOWN;
    }

    private List<VulnerabilityFinding> scanForSecurityIssues(String content, String filePath,
                                                             String fileName, Language language) {
        List<VulnerabilityFinding> findings = new ArrayList<>();

        String[] lines = content.split("\\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNumber = i + 1;

            findings.addAll(checkForSecrets(line, filePath, lineNumber));
            findings.addAll(checkForHardcodedConfigs(line, filePath, lineNumber));
            findings.addAll(checkForSqlInjection(line, filePath, lineNumber, language));
            findings.addAll(checkForCommandInjection(line, filePath, lineNumber, language));
            findings.addAll(checkForEvalUsage(line, filePath, lineNumber, language));
            findings.addAll(checkForWeakCrypto(line, filePath, lineNumber, language));
            findings.addAll(checkForWeakRandom(line, filePath, lineNumber, language));
            findings.addAll(checkForDebugStatements(line, filePath, lineNumber, language));
        }

        return findings;
    }

    private List<VulnerabilityFinding> checkForSecrets(String line, String filePath, int lineNumber) {
        List<VulnerabilityFinding> findings = new ArrayList<>();

        if (HARDCODED_PASSWORD.matcher(line).find()) {
            findings.add(createFinding(
                    filePath,
                    Severity.CRITICAL,
                    "Hardcoded Password",
                    "Line " + lineNumber + ": Potential hardcoded password detected",
                    line.trim()
            ));
        }

        if (HARDCODED_API_KEY.matcher(line).find()) {
            findings.add(createFinding(
                    filePath,
                    Severity.CRITICAL,
                    "Hardcoded API Key",
                    "Line " + lineNumber + ": Potential hardcoded API key detected",
                    line.trim()
            ));
        }

        if (JWT_TOKEN.matcher(line).find()) {
            findings.add(createFinding(
                    filePath,
                    Severity.HIGH,
                    "Potential JWT Token",
                    "Line " + lineNumber + ": JWT token may be hardcoded",
                    line.trim()
            ));
        }

        if (AWS_KEY.matcher(line).find()) {
            findings.add(createFinding(
                    filePath,
                    Severity.CRITICAL,
                    "AWS Access Key",
                    "Line " + lineNumber + ": AWS access key detected",
                    line.trim()
            ));
        }

        if (GENERIC_SECRET.matcher(line).find()) {
            findings.add(createFinding(
                    filePath,
                    Severity.HIGH,
                    "Potential Secret",
                    "Line " + lineNumber + ": Generic secret pattern detected",
                    line.trim()
            ));
        }

        return findings;
    }

    private List<VulnerabilityFinding> checkForHardcodedConfigs(String line, String filePath, int lineNumber) {
        List<VulnerabilityFinding> findings = new ArrayList<>();

        if (IP_ADDRESS.matcher(line).find() && !line.contains("localhost") && !line.contains("example")) {
            findings.add(createFinding(
                    filePath,
                    Severity.LOW,
                    "Hardcoded IP Address",
                    "Line " + lineNumber + ": Hardcoded IP address detected",
                    line.trim()
            ));
        }

        if (INTERNAL_URL.matcher(line).find() && !line.contains("localhost:8080")) {
            findings.add(createFinding(
                    filePath,
                    Severity.INFO,
                    "Internal URL Reference",
                    "Line " + lineNumber + ": Internal URL detected",
                    line.trim()
            ));
        }

        if (HARDcoded_IP.matcher(line).find()) {
            findings.add(createFinding(
                    filePath,
                    Severity.MEDIUM,
                    "Hardcoded Configuration",
                    "Line " + lineNumber + ": Hardcoded host/IP configuration",
                    line.trim()
            ));
        }

        return findings;
    }

    private List<VulnerabilityFinding> checkForSqlInjection(String line, String filePath, int lineNumber, Language language) {
        List<VulnerabilityFinding> findings = new ArrayList<>();

        Pattern pattern = switch (language) {
            case JAVA -> SQL_INJECTION_JAVA;
            case PYTHON -> SQL_INJECTION_PYTHON;
            case JAVASCRIPT, TYPESCRIPT -> SQL_INJECTION_JS;
            case PHP -> Pattern.compile("(?i)(mysql_query|pg_query|db_query)\\s*\\([^)]*\\$|\\\".*\\$\\w+.*\\\"");
            default -> null;
        };

        if (pattern != null && pattern.matcher(line).find()) {
            findings.add(createFinding(
                    filePath,
                    Severity.HIGH,
                    "Potential SQL Injection",
                    "Line " + lineNumber + ": SQL query with string concatenation detected",
                    line.trim()
            ));
        }

        return findings;
    }

    private List<VulnerabilityFinding> checkForCommandInjection(String line, String filePath, int lineNumber, Language language) {
        List<VulnerabilityFinding> findings = new ArrayList<>();

        Pattern pattern = switch (language) {
            case JAVA -> COMMAND_INJECTION_JAVA;
            case PYTHON -> COMMAND_INJECTION_PYTHON;
            case JAVASCRIPT, TYPESCRIPT -> COMMAND_INJECTION_JS;
            case PHP -> COMMAND_INJECTION_PHP;
            case RUBY -> Pattern.compile("(?i)\\(system|exec|`.*\\`|%x\\()");
            case GO -> Pattern.compile("(?i)exec\\.Command");
            default -> null;
        };

        if (pattern != null && pattern.matcher(line).find()) {
            findings.add(createFinding(
                    filePath,
                    Severity.HIGH,
                    "Potential Command Injection",
                    "Line " + lineNumber + ": Command execution with potentially unsanitized input",
                    line.trim()
            ));
        }

        return findings;
    }

    private List<VulnerabilityFinding> checkForEvalUsage(String line, String filePath, int lineNumber, Language language) {
        List<VulnerabilityFinding> findings = new ArrayList<>();

        Pattern pattern = switch (language) {
            case JAVA -> EVAL_JAVA;
            case PYTHON -> EVAL_PYTHON;
            case JAVASCRIPT, TYPESCRIPT -> EVAL_JS;
            case PHP -> EVAL_PHP;
            case RUBY -> Pattern.compile("(?i)eval\\s*\\(|instance_eval\\s*\\(");
            default -> null;
        };

        if (pattern != null && pattern.matcher(line).find()) {
            findings.add(createFinding(
                    filePath,
                    Severity.CRITICAL,
                    "Dangerous Code Execution",
                    "Line " + lineNumber + ": eval/exec usage detected",
                    line.trim()
            ));
        }

        return findings;
    }

    private List<VulnerabilityFinding> checkForWeakCrypto(String line, String filePath, int lineNumber, Language language) {
        List<VulnerabilityFinding> findings = new ArrayList<>();

        Pattern pattern = switch (language) {
            case JAVA -> WEAK_CRYPTO_JAVA;
            case PYTHON -> WEAK_CRYPTO_PYTHON;
            case JAVASCRIPT, TYPESCRIPT -> WEAK_CRYPTO_JS;
            case PHP -> Pattern.compile("(?i)md5\\s*\\(|sha1\\s*\\(|crypt\\s*\\(");
            case RUBY -> Pattern.compile("(?i)Digest::(MD5|SHA1)\\.new|OpenSSL::Cipher::(?:DES|ARC4)");
            default -> null;
        };

        if (pattern != null && pattern.matcher(line).find()) {
            findings.add(createFinding(
                    filePath,
                    Severity.MEDIUM,
                    "Weak Cryptographic Algorithm",
                    "Line " + lineNumber + ": Weak crypto algorithm detected",
                    line.trim()
            ));
        }

        return findings;
    }

    private List<VulnerabilityFinding> checkForWeakRandom(String line, String filePath, int lineNumber, Language language) {
        List<VulnerabilityFinding> findings = new ArrayList<>();

        Pattern pattern = switch (language) {
            case JAVA -> WEAK_RANDOM_JAVA;
            case PYTHON -> WEAK_RANDOM_PYTHON;
            case JAVASCRIPT, TYPESCRIPT -> WEAK_RANDOM_JS;
            case PHP -> Pattern.compile("(?i)mt_rand|rand\\s*\\(");
            case GO -> Pattern.compile("(?i)math/rand|rand\\.");
            default -> null;
        };

        if (pattern != null && pattern.matcher(line).find()) {
            findings.add(createFinding(
                    filePath,
                    Severity.LOW,
                    "Weak Random Number Generator",
                    "Line " + lineNumber + ": Weak random number generator detected",
                    line.trim()
            ));
        }

        return findings;
    }

    private List<VulnerabilityFinding> checkForDebugStatements(String line, String filePath, int lineNumber, Language language) {
        List<VulnerabilityFinding> findings = new ArrayList<>();

        Pattern pattern = switch (language) {
            case JAVA -> DEBUG_STATEMENT_JAVA;
            case PYTHON -> DEBUG_STATEMENT_PYTHON;
            case JAVASCRIPT, TYPESCRIPT -> DEBUG_STATEMENT_JS;
            case PHP -> Pattern.compile("(?i)var_dump|print_r|echo\\s*\\$|die\\s*\\(|var_export");
            case RUBY -> Pattern.compile("(?i)puts|p\\s\\(|raise|binding\\.pry|byebug");
            case GO -> Pattern.compile("(?i)fmt\\.Print|log\\.Print");
            default -> null;
        };

        if (pattern != null && pattern.matcher(line).find()) {
            findings.add(createFinding(
                    filePath,
                    Severity.LOW,
                    "Debug Statement",
                    "Line " + lineNumber + ": Debug statement detected",
                    line.trim()
            ));
        }

        return findings;
    }

    private VulnerabilityFinding createFinding(String location, Severity severity, String title,
                                               String description, String evidence) {
        return VulnerabilityFinding.of(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new TenantId(UUID.randomUUID()),
                title,
                severity,
                "SAST",
                location,
                description + " | " + evidence,
                Instant.now()
        );
    }
}
