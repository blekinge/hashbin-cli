package net.hashbin.cli.command;

import io.quarkus.info.BuildInfo;
import io.quarkus.info.GitInfo;
import io.quarkus.info.JavaInfo;
import io.quarkus.info.OsInfo;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import picocli.CommandLine;

import java.text.MessageFormat;

/** Provides version and runtime environment information for Picocli's {@code --version} option. */
@Singleton
class VersionProvider implements CommandLine.IVersionProvider {

    /** Build information exposed by Quarkus. */
    @Inject
    BuildInfo buildInfo;

    /**
     * Git information, injected as an {@link Instance} because the {@code GitInfo} bean only
     * exists when git metadata is available at build time (e.g. absent in a container/CI build
     * from a source copy with no {@code .git}); the commit id is then simply omitted.
     */
    @Inject
    Instance<GitInfo> gitInfo;

    /** Java runtime information exposed by Quarkus. */
    @Inject
    JavaInfo javaInfo;

    /** Operating system information exposed by Quarkus. */
    @Inject
    OsInfo osInfo;

    @Override
    public String[] getVersion() {
        String versionLine =
                gitInfo.isUnsatisfied()
                        ? MessageFormat.format(
                                "{0} {1}, built at {2}",
                                buildInfo.artifact(), buildInfo.version(), buildInfo.time())
                        : MessageFormat.format(
                                "{0} {1} ({3}), built at {2}",
                                buildInfo.artifact(),
                                buildInfo.version(),
                                buildInfo.time(),
                                gitInfo.get().latestCommitId());
        return new String[]{
                versionLine,
                MessageFormat.format("Quarkus: {0}", buildInfo.quarkusVersion()),
                MessageFormat.format(
                        "Java version: {0}, vendor: {1}, vendorVersion: {2}",
                        javaInfo.version(), javaInfo.vendor(), javaInfo.vendorVersion()),
                MessageFormat.format(
                        "OS name: \"{0}\", version: \"{1}\", arch: \"{2}\"",
                        osInfo.name(), osInfo.version(), osInfo.architecture())
        };
    }
}
