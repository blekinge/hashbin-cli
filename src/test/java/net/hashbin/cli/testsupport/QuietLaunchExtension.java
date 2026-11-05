package net.hashbin.cli.testsupport;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.function.BiPredicate;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import io.quarkus.dev.console.QuarkusConsole;
import io.quarkus.test.junit.main.QuarkusMainIntegrationTest;
import io.quarkus.test.junit.main.QuarkusMainTest;

/**
 * Keeps successful {@code @QuarkusMainTest} command runs quiet, without hiding anything from
 * the console when something goes wrong.
 *
 * <p>Quarkus's test framework echoes every launched command's output to the test console, with
 * no configuration to turn it off — so {@code license}/{@code changelog} dump whole documents,
 * and the integration launcher prints its own {@code Executing "…"} line, on every green run.
 * The fix depends on how the command is executed, so this extension branches per test class:
 *
 * <ul>
 *   <li><b>in-JVM</b> (a plain {@code @QuarkusMainTest}): the test extension installs a
 *       {@code QuarkusConsole} filter that captures stdout/stderr into {@code LaunchResult}
 *       <em>and</em> echoes it. This extension adds a second filter that always returns
 *       {@code false}; QuarkusConsole writes a line to the console only when <em>all</em> filters
 *       allow it, so the echo is suppressed while capture, assertions and failure messages are
 *       untouched.</li>
 *   <li><b>external process</b> (a {@code @QuarkusMainIntegrationTest}): the launcher writes
 *       straight to the test JVM's {@code System.out}/{@code System.err}, which no in-JVM filter
 *       can intercept — so those are swapped for a throwaway buffer for the duration of the test
 *       and dumped back only if the test did not pass.</li>
 * </ul>
 *
 * <p>Auto-detected as a <em>global</em> (root) extension (see {@code META-INF/services}), so every
 * command test is quiet by default with nothing to register. It must be a root extension because
 * the external-process swap has to wrap the launcher's own {@code beforeEach}. The
 * {@code @QuarkusMainTest} / {@code @QuarkusMainIntegrationTest} guards leave every other test
 * untouched.
 */
@Order(Integer.MIN_VALUE)
public class QuietLaunchExtension implements BeforeEachCallback, AfterEachCallback {

    /** QuarkusConsole output filter: returning {@code false} suppresses the in-JVM echo of a launch. */
    private final BiPredicate<String, Boolean> suppressEcho = (record, errorStream) -> false;

    /**
     * The active suppression for the test running on this thread: a {@link Swap} for an
     * external-process launch, or {@link Boolean#TRUE} when the in-JVM {@code QuarkusConsole}
     * filter was installed.
     */
    private static final ThreadLocal<Object> ACTIVE = new ThreadLocal<>();

    /** Diversion of {@code System.out}/{@code System.err} into a discardable buffer for one external launch. */
    private static final class Swap {
        final PrintStream originalOut = System.out;
        final PrintStream originalErr = System.err;
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        Swap() {
            var sink = new PrintStream(buffer, true);
            System.setOut(sink);
            System.setErr(sink);
        }
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        Class<?> testClass = context.getRequiredTestClass();
        if (testClass.isAnnotationPresent(QuarkusMainIntegrationTest.class)) {
            ACTIVE.set(new Swap());
        } else if (testClass.isAnnotationPresent(QuarkusMainTest.class)) {
            // Runs before the LaunchResult parameter (and with it the launch itself) is resolved.
            QuarkusConsole.addOutputFilter(suppressEcho);
            ACTIVE.set(Boolean.TRUE);
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        Object active = ACTIVE.get();
        if (active == null) {
            return;
        }
        ACTIVE.remove();
        if (active == Boolean.TRUE) {
            QuarkusConsole.removeOutputFilter(suppressEcho);
            return;
        }
        Swap swap = (Swap) active;
        System.setOut(swap.originalOut);
        System.setErr(swap.originalErr);
        // A launch that crashes throws from a beforeEach callback, and a failing assertion from the
        // test method; both surface here as an execution exception. Keep the diverted output for
        // anything that did not pass, drop it (staying quiet) when the test was green.
        if (context.getExecutionException().isPresent() && swap.buffer.size() > 0) {
            System.out.println("[test output] " + context.getRequiredTestClass().getSimpleName()
                    + " — test did not pass, echoing the diverted launch output:");
            System.out.print(swap.buffer.toString(StandardCharsets.UTF_8));
            System.out.flush();
        }
    }
}
