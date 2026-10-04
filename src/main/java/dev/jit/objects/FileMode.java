package dev.jit.objects;

/** The mode written in front of each tree entry. Git only ever uses these four. */
public enum FileMode {
    REGULAR("100644", ObjectType.BLOB),
    EXECUTABLE("100755", ObjectType.BLOB),
    SYMLINK("120000", ObjectType.BLOB),        // blob body = the link's target path
    DIRECTORY("40000", ObjectType.TREE);       // no leading zero on disk; ls-tree pads it to "040000"

    private final String wireName;             // the exact text written inside the tree object
    private final ObjectType objectType;       // what kind of object the entry's id points at

    FileMode(String wireName, ObjectType objectType) {
        this.wireName = wireName;
        this.objectType = objectType;
    }

    public String wireName()       { return wireName; }
    public ObjectType objectType() { return objectType; }

    /** As git prints it: always 6 digits. */
    public String displayName() { return "0".repeat(6 - wireName.length()) + wireName; }

    public static FileMode fromWireName(String s) {
        for (FileMode m : values()) if (m.wireName.equals(s)) return m;
        throw new IllegalStateException("unknown file mode: " + s);
    }
}
