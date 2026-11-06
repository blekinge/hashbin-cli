package net.hashbin.cli.command;

import io.quarkus.runtime.annotations.RegisterResources;
import picocli.CommandLine.Command;

/** Prints the bundled {@code LICENSE.txt} (copied onto the classpath by {@code pom.xml}). */
@RegisterResources(globs = {"LICENSE.txt"} /* resources are not in the native image by default */)
@Command(name = "license", description = "Show the license")
class LicenseCommand implements Runnable {

    @Override
    public void run() {
        ResourcePrinter.printFileFromClassPath("LICENSE.txt", System.out);
    }
}
