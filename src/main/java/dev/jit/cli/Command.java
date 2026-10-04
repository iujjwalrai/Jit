package dev.jit.cli;

public interface Command {
    String name();                              // word typed after "jit"
    String usage();                             // shown in --help
    int run(String[] args) throws Exception;    // returns exit code
}

