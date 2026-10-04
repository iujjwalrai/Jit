package dev.jit.ignore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class IgnoresTest {

    @TempDir Path root;

    private static boolean rule(String pattern, String path, boolean isDir) {
        return IgnoreRule.parse(pattern).orElseThrow().matches(path, isDir);
    }

    @Test
    void globs() {
        assertTrue(rule("*.log", "a.log", false));
        assertTrue(rule("*.log", "deep/down/a.log", false));       // no slash: matches the name at any depth
        assertFalse(rule("*.log", "a.log.txt", false));
        assertTrue(rule("?.txt", "a.txt", false));
        assertFalse(rule("?.txt", "ab.txt", false));
        assertTrue(rule("[ab].c", "b.c", false));
        assertFalse(rule("[!ab].c", "b.c", false));
        assertTrue(rule("[!ab].c", "x.c", false));
        assertTrue(rule("a+b(1).txt", "a+b(1).txt", false));       // regex characters are literal
    }

    @Test
    void anchoring() {
        assertTrue(rule("/b.txt", "b.txt", false));
        assertFalse(rule("/b.txt", "sub/b.txt", false));           // leading slash: only at this level
        assertTrue(rule("docs/*.md", "docs/a.md", false));
        assertFalse(rule("docs/*.md", "x/docs/a.md", false));      // middle slash anchors too
        assertFalse(rule("docs/*.md", "docs/deep/a.md", false));   // * doesn't cross /
    }

    @Test
    void doubleStar() {
        assertTrue(rule("**/foo", "foo", true));
        assertTrue(rule("**/foo", "a/b/foo", false));
        assertTrue(rule("a/**", "a/x/y", false));
        assertFalse(rule("a/**", "a", true));
        assertTrue(rule("a/**/b", "a/b", false));                  // zero folders in between
        assertTrue(rule("a/**/b", "a/x/y/b", false));
        assertTrue(rule("docs/**/*.tmp", "docs/deep/c.tmp", false));
    }

    @Test
    void dirOnlyAndComments() {
        assertTrue(rule("build/", "build", true));
        assertFalse(rule("build/", "build", false));               // a file named build is not ignored
        assertTrue(IgnoreRule.parse("# comment").isEmpty());
        assertTrue(IgnoreRule.parse("   ").isEmpty());
        assertTrue(rule("\\#hash", "#hash", false));
        assertTrue(rule("trailing   ", "trailing", false));        // trailing spaces dropped
    }

    @Test
    void lastMatchWinsAndDeeperFilesOverride() throws Exception {
        Files.writeString(root.resolve(".jitignore"), "*.log\n!important.log\n*.md\n");
        Files.createDirectories(root.resolve("docs"));
        Files.writeString(root.resolve("docs/.jitignore"), "!a.md\n");
        Files.createDirectories(root.resolve(".jit/info"));
        Files.writeString(root.resolve(".jit/info/exclude"), "secret.txt\n*.tmp\n");
        Files.writeString(root.resolve(".jitignore"), "*.log\n!important.log\n*.md\n!keep.tmp\n");
        Ignores ig = Ignores.load(root, root.resolve(".jit"));

        assertTrue(ig.matches("x.log", false));
        assertFalse(ig.matches("logs/important.log", false));      // negated later in the same file
        assertTrue(ig.matches("readme.md", false));
        assertFalse(ig.matches("docs/a.md", false));               // docs/.jitignore beats the root file
        assertTrue(ig.matches("docs/b.md", false));
        assertTrue(ig.matches("secret.txt", false));               // from .jit/info/exclude
        assertFalse(ig.matches("keep.tmp", false));                // .jitignore beats info/exclude
        assertFalse(ig.matches("src/Main.java", false));
    }

    @Test
    void isIgnoredLooksAtParentFolders() throws Exception {
        Files.writeString(root.resolve(".jitignore"), "build/\n");
        Ignores ig = Ignores.load(root, root.resolve(".jit"));
        assertFalse(ig.matches("build/x.o", false));               // the rule itself only names the folder
        assertTrue(ig.isIgnored("build/x.o", false));              // but everything inside it is ignored
        assertFalse(Ignores.none().isIgnored("build/x.o", false));
    }
}
