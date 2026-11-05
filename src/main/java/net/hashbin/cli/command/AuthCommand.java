package net.hashbin.cli.command;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

import net.hashbin.cli.client.ApiException;
import net.hashbin.cli.model.AuthResponse;
import net.hashbin.cli.model.MeView;
import net.hashbin.cli.store.CredentialsStore;
import net.hashbin.cli.util.Cli;
import net.hashbin.cli.util.Console;
import net.hashbin.cli.util.Passwords;

/**
 * {@code hashbin auth ...} — native email + password auth (register/login/me).
 * Register and login store the returned bearer token in {@link CredentialsStore}
 * so later commands, and {@code stamp} attribution, pick it up automatically.
 *
 * <p>Subcommands are nested static classes and reach the global options via the
 * {@code root} parent chain: {@code Me → AuthCommand → HashbinCommand}.
 */
@Command(name = "auth",
        description = "Manage your hashbin account",
        subcommands = { AuthCommand.Register.class, AuthCommand.Login.class, AuthCommand.Me.class })
public class AuthCommand implements Runnable {

    @ParentCommand
    HashbinCommand root;

    @Spec
    picocli.CommandLine.Model.CommandSpec spec;

    @Override
    public void run() {
        spec.commandLine().usage(System.err);
    }

    /** Shared email/password + token-persistence handling for register + login. */
    abstract static class CredentialsBase implements Callable<Integer> {
        @ParentCommand
        AuthCommand auth;

        @Option(names = "--email", paramLabel = "EMAIL", required = true, description = "Account email")
        String email;

        @Option(names = "--password", paramLabel = "PASSWORD",
                description = "Password (omit to be prompted, or set HASHBIN_PASSWORD)")
        String password;

        HashbinCommand root() {
            return auth.root;
        }

        String resolvePassword() {
            if (password != null && !password.isBlank()) {
                return password;
            }
            String env = System.getenv("HASHBIN_PASSWORD");
            if (env != null && !env.isBlank()) {
                return env;
            }
            char[] typed = Passwords.readPassword("Password: ");
            return typed == null ? null : new String(typed);
        }

        void persist(AuthResponse r) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("api_base", root().resolveApiBase());
            fields.put("email", r.email != null ? r.email : email);
            fields.put("user_id", r.userId);
            fields.put("token", r.token);
            new CredentialsStore().save(fields);
        }
    }

    @Command(name = "register", description = "Create an account and store its token")
    static class Register extends CredentialsBase {
        @Override
        public Integer call() {
            String pass = resolvePassword();
            if (pass == null) {
                System.out.println(Console.red("✗ ") + "A password is required.");
                return 1;
            }
            try {
                AuthResponse r = root().client().register(email, pass);
                persist(r);
                Cli.ok("Registered " + r.email + " — token stored.");
                return 0;
            } catch (ApiException e) {
                return Cli.fail(e);
            }
        }
    }

    @Command(name = "login", description = "Sign in and store the access token")
    static class Login extends CredentialsBase {
        @Override
        public Integer call() {
            String pass = resolvePassword();
            if (pass == null) {
                System.out.println(Console.red("✗ ") + "A password is required.");
                return 1;
            }
            try {
                AuthResponse r = root().client().login(email, pass);
                persist(r);
                Cli.ok("Logged in as " + r.email + " — token stored.");
                return 0;
            } catch (ApiException e) {
                return Cli.fail(e);
            }
        }
    }

    @Command(name = "me", description = "Show the signed-in account")
    static class Me implements Callable<Integer> {
        @ParentCommand
        AuthCommand auth;

        @Override
        public Integer call() {
            HashbinCommand root = auth.root;
            String token = root.resolveToken();
            if (token == null) {
                System.out.println(Console.red("✗ ") + "Not signed in. Run `hashbin auth login`.");
                return 1;
            }
            try {
                MeView me = root.client().me(token);
                Cli.ok(me.email + "  " + Console.dim("(id: " + me.userId + ")"));
                return 0;
            } catch (ApiException e) {
                return Cli.fail(e);
            }
        }
    }
}
