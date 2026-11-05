package net.hashbin.cli.testsupport;

import java.io.PrintStream;

import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Prints the name of each test method to the console as it starts.
 *
 * <p>Surefire only ever reports per <em>class</em>; with this extension every launch becomes
 * traceable to the method that caused it, and if the JVM ever dies mid-suite the last printed
 * line names the test that was running.
 *
 * <p>Registered globally (for every test in the module) through JUnit's extension
 * auto-detection: the class name is listed in
 * {@code META-INF/services/org.junit.jupiter.api.extension.Extension} and the
 * {@code junit.jupiter.extensions.autodetection.enabled} system property is set in the surefire
 * and failsafe configurations of {@code pom.xml}.
 */
public class TestMethodReporter implements BeforeEachCallback {

    private static final PrintStream CONSOLE = System.out;

    @Override
    public void beforeEach(ExtensionContext context) {
        // Runs before any @Launch has been resolved (the launch itself happens during parameter
        // resolution), so the line appears before the command's (silenced) output would.
        CONSOLE.printf("[test] %s#%s%n",
                       context.getRequiredTestClass().getSimpleName(),
                       context.getDisplayName());
    }
}
