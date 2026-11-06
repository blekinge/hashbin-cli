package net.hashbin.cli.command;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainIntegrationTest;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Test;

/**
 * Runs the packaged artifact (the über-jar, or the native executable under {@code -Dnative}) in a
 * separate process, so real start-up and — most importantly for a batch tool — <em>process exit
 * codes</em> are verified end to end, not just the in-JVM {@code @QuarkusMainTest} behaviour.
 *
 * <p>Deliberately drives only the backend-free infrastructure commands ({@code --version}, a usage
 * error), so it stays a real-process smoke test. Per-command behaviour and the wire contract are
 * covered by {@code HashbinCliContractTest} and {@link MainCommandTest}.
 *
 * <p>Executed by failsafe during {@code ./mvnw verify}.
 */
@QuarkusMainTest
@QuarkusMainIntegrationTest
class CommandLineIT {

    @Test
    @Launch("--version")
    void testVersion(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput()).contains("hashbin-cli");
    }

    @Test
    @Launch(
            value = {"license", "--nope"},
            exitCode = 2)
    void testUsageErrorExitsNonZero(LaunchResult result) {
        // Proves Picocli's exit code survives to the real process exit status (a usage error is
        // 2), which is what a Job/CronJob keys off.
        assertThat(result.exitCode()).isEqualTo(2);
    }
}
