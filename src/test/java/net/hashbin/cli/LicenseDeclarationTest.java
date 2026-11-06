package net.hashbin.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Checks that the license declared in {@code pom.xml} is the license the project actually ships,
 * i.e. the one in {@code LICENSE.txt} (which the {@code license} command prints at runtime).
 *
 * <p>The expected name is <em>derived from</em> {@code LICENSE.txt} rather than hard-coded, so
 * replacing the license file makes this fail until {@code pom.xml} is updated to agree. Inspects
 * the source tree; skipped when no {@code pom.xml} is present (a build from a packaged artifact).
 */
class LicenseDeclarationTest {

    /** The {@code <name>} inside {@code <licenses>} in {@code pom.xml}. */
    private static final Pattern POM_LICENSE_NAME =
            Pattern.compile("(?s)<licenses>.*?<name>\\s*(.*?)\\s*</name>.*?</licenses>");

    @Test
    void testPomLicenseMatchesLicenseFile() throws IOException {
        Path pom = Path.of("pom.xml");
        Path licenseFile = Path.of("LICENSE.txt");
        assumeTrue(
                Files.isRegularFile(pom) && Files.isRegularFile(licenseFile),
                "no source tree available (packaged build); nothing to cross-check");

        String declared = declaredLicenseName(Files.readString(pom));
        assertThat(declared).as("pom.xml must declare a <licenses><name>").isNotBlank();

        assertThat(declared)
                .as("pom.xml <licenses><name> must match the title and version in LICENSE.txt")
                .isEqualTo(licenseNameFromText(Files.readString(licenseFile)));
    }

    private static String declaredLicenseName(String pomXml) {
        Matcher matcher = POM_LICENSE_NAME.matcher(pomXml);
        return matcher.find() ? matcher.group(1) : "";
    }

    /**
     * Builds the canonical name from a license text: its first non-blank line (the title, e.g.
     * {@code "Apache License"}) followed by the version line up to its first comma ({@code "Version
     * 2.0"}), giving {@code "Apache License, Version 2.0"}.
     */
    private static String licenseNameFromText(String licenseText) {
        String title =
                licenseText
                        .lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty())
                        .findFirst()
                        .orElse("");
        String version =
                licenseText
                        .lines()
                        .map(String::trim)
                        .filter(line -> line.startsWith("Version "))
                        .findFirst()
                        .map(line -> line.split(",")[0].trim())
                        .orElse("");
        return title + ", " + version;
    }
}
