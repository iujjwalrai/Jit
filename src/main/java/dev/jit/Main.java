package dev.jit;

import dev.jit.cli.Command;
import dev.jit.cli.InitCommand;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Main {
    private static final Map<String, Command> COMMANDS = new LinkedHashMap<>();

    static {
        register(new InitCommand());
        register(new HashObjectCommand());
        register(new CatFileCommand());
    }

    private static void register(Command c) { COMMANDS.put(c.name(), c); }

    public static void main(String[] args) {
        if (args.length == 0 || args[0].equals("--help")) {
            System.out.println("usage: jit <command> [<args>]\n");
            COMMANDS.values().forEach(c -> System.out.printf("   %-12s %s%n", c.name(), c.usage()));
            System.exit(0);
        }
        Command cmd = COMMANDS.get(args[0]);
        if (cmd == null) {
            System.err.println("jit: '" + args[0] + "' is not a jit command.");
            System.exit(1);
        }
        try {
            System.exit(cmd.run(Arrays.copyOfRange(args, 1, args.length)));
        } catch (Exception e) {
            System.err.println("fatal: " + e.getMessage());
            if (System.getenv("JIT_DEBUG") != null) e.printStackTrace();
            System.exit(128);   // git's exit code for fatal errors
        }
    }
}