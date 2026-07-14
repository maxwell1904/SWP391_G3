package com.swp391.backend;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BackendApplication {

    public static void main(String[] args) {
        LocalEnvironmentLoader.load();
        SpringApplication.run(BackendApplication.class, args);
    }

    /**
     * Lets the normal Maven run command use the repository's local configuration
     * without putting credentials in application.yml. Explicit environment variables
     * and -D properties always take precedence over these local files.
     */
    private static final class LocalEnvironmentLoader {
        private static final Set<String> loadedKeys = new HashSet<>();

        private LocalEnvironmentLoader() {
        }

        static void load() {
            Path workingDirectory = Path.of("").toAbsolutePath().normalize();
            Path projectDirectory = workingDirectory.getFileName() != null && "backend".equals(workingDirectory.getFileName().toString())
                    ? workingDirectory.getParent()
                    : workingDirectory;
            if (projectDirectory == null) {
                return;
            }

            for (Path file : List.of(projectDirectory.resolve(".env"), projectDirectory.resolve(".env.local"))) {
                loadFile(file);
            }
        }

        private static void loadFile(Path file) {
            if (!Files.isRegularFile(file)) {
                return;
            }

            try {
                for (String line : Files.readAllLines(file)) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    int separator = trimmed.indexOf('=');
                    if (separator < 1) {
                        continue;
                    }

                    String name = trimmed.substring(0, separator).trim();
                    String value = unquote(trimmed.substring(separator + 1).trim());
                    if (!name.matches("[A-Za-z_][A-Za-z0-9_]*") || System.getenv(name) != null) {
                        continue;
                    }
                    if (System.getProperty(name) == null || loadedKeys.contains(name)) {
                        System.setProperty(name, value);
                        loadedKeys.add(name);
                    }
                }
            } catch (IOException ignored) {
                // Local configuration is optional; Spring's normal property sources remain available.
            }
        }

        private static String unquote(String value) {
            if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
                return value.substring(1, value.length() - 1);
            }
            return value;
        }
    }
}
