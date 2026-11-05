package net.hashbin.cli.command;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;

/**
 * In-JVM tests for the command-tree infrastructure that needs no backend: {@code --version}
 * (via {@link VersionProvider}), {@code --help}, and the bundled-resource {@code license} and
 * {@code changelog} commands.
 */
@QuarkusMainTest
class MainCommandTest {

    @Test
    @Launch("--version")
    void testVersion(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        // Version-independent labels only; the values come from quarkus-info.
        assertThat(result.getOutput())
                .contains("hashbin-cli")
                .contains("Quarkus:")
                .contains("Java version:")
                .contains("OS name:");
    }

    @Test
    @Launch("--help")
    void testHelp(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("Usage: hashbin")
                .contains("changelog", "license", "help")
                .contains("health", "stamp", "verify", "explore");
    }

    @Test
    @Launch("license")
    void testLicense(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput()).isNotBlank().contains("Apache License");
    }

    @Test
    @Launch("changelog")
    void testChangelog(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput()).contains("Changelog");
    }
}
