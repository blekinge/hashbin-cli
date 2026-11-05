package net.hashbin.cli.command;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;

/** Helper that prints a classpath resource (such as the license or changelog) to an output stream. */
final class ResourcePrinter {

    private ResourcePrinter() {
    }

    /**
     * Copies a classpath resource to the given output stream. If the resource is absent
     * (for example not packaged into a native image), a blank line is written rather than
     * throwing, so the command stays usable.
     */
    static void printFileFromClassPath(final String name, final PrintStream out) {
        try (var resource =
                     Thread.currentThread().getContextClassLoader().getResourceAsStream(name)) {
            if (resource != null) {
                resource.transferTo(out);
            }
            out.println();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
