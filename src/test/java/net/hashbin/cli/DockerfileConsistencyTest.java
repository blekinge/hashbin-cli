package net.hashbin.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Guards the JVM runtime recipe that the two container builds share.
 *
 * <p>There are two ways to get a JVM image for this project, and they cannot share a single file:
 * {@code src/main/docker/Dockerfile.jvm} {@code COPY}s an über-jar built on the host and is what
 * {@code ./mvnw quarkus:image-build} uses (it resolves that path <em>by name</em>); the CI recipe
 * {@code Dockerfile.ci} compiles inside the image and its {@code final} stage is what CI tags and
 * pushes. Since the runtime half of those two files has to say the same thing, this test says so
 * for them: base image, user, entrypoint and artifact coordinates must agree. The {@code COPY}
 * lines are deliberately <em>not</em> compared (they differ by necessity).
 *
 * <p>Skips when {@code Dockerfile.ci} is absent (it lives in the CI wiring, added separately) or
 * when no source tree is available.
 */
class DockerfileConsistencyTest {

    private static final Path CI_DOCKERFILE = Path.of("src", "main", "docker", "Dockerfile.ci");
    private static final Path JVM_DOCKERFILE = Path.of("src", "main", "docker", "Dockerfile.jvm");

    @Test
    void testRuntimeStagesAgree() throws IOException {
        assumeTrue(
                Files.isRegularFile(CI_DOCKERFILE) && Files.isRegularFile(JVM_DOCKERFILE),
                "no CI Dockerfile / source tree available; nothing to cross-check");

        List<String> ci = finalStage(Files.readString(CI_DOCKERFILE));
        List<String> jvm = Files.readAllLines(JVM_DOCKERFILE);

        assertThat(baseImage(jvm))
                .as(
                        "runtime base image: Dockerfile.ci's `final` stage and Dockerfile.jvm must be the same image")
                .isEqualTo(baseImage(ci));
        assertThat(directive(jvm, "ENTRYPOINT"))
                .as("entrypoint: both images must forward arguments to the CLI the same way")
                .isEqualTo(directive(ci, "ENTRYPOINT"));
        assertThat(directive(jvm, "USER"))
                .as("user: both images must run as the same uid")
                .isEqualTo(directive(ci, "USER"));
        for (String arg : List.of("APP_NAME", "APP_VERSION")) {
            assertThat(argValue(jvm, arg))
                    .as("Dockerfile.jvm's default for %s must match Dockerfile.ci's", arg)
                    .isEqualTo(argValue(ci, arg));
        }
    }

    private static List<String> finalStage(String dockerfile) {
        List<String> lines = dockerfile.lines().toList();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.regionMatches(true, 0, "FROM", 0, 4)
                    && line.toLowerCase().matches(".*\\bas\\s+final$")) {
                return lines.subList(i, lines.size());
            }
        }
        throw new AssertionError("Dockerfile.ci has no `FROM ... AS final` stage");
    }

    private static String baseImage(List<String> lines) {
        String from = directive(lines, "FROM");
        return from == null ? null : normalise(from.replaceAll("(?i)\\s+AS\\s+\\S+$", ""));
    }

    private static String directive(List<String> lines, String name) {
        return lines.stream()
                .map(String::trim)
                .filter(line -> !line.startsWith("#"))
                .filter(line -> line.regionMatches(true, 0, name, 0, name.length()))
                .filter(
                        line ->
                                line.length() == name.length()
                                        || Character.isWhitespace(line.charAt(name.length())))
                .map(line -> line.substring(name.length()).trim())
                .map(DockerfileConsistencyTest::normalise)
                .findFirst()
                .orElse(null);
    }

    private static String argValue(List<String> lines, String name) {
        return lines.stream()
                .map(String::trim)
                .filter(line -> line.regionMatches(true, 0, "ARG", 0, 3))
                .map(line -> line.substring(3).trim())
                .filter(assign -> assign.startsWith(name + "="))
                .map(assign -> assign.substring(name.length() + 1).trim())
                .findFirst()
                .orElse(null);
    }

    private static String normalise(String line) {
        return line.replaceAll("\\s+", " ");
    }
}
