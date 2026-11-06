package net.hashbin.cli.command;

import io.quarkus.runtime.annotations.RegisterResources;
import picocli.CommandLine;

/** Prints the bundled {@code CHANGELOG.md} (copied onto the classpath by {@code pom.xml}). */
@RegisterResources(globs = {"CHANGELOG.md"} /* resources are not in the native image by default */)
@CommandLine.Command(name = "changelog", description = "Show the changelog")
class ChangelogCommand implements Runnable {

    @Override
    public void run() {
        ResourcePrinter.printFileFromClassPath("CHANGELOG.md", System.out);
    }
}
