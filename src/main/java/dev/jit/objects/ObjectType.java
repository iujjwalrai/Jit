package dev.jit.objects;

public enum ObjectType {
    BLOB("blob"), TREE("tree"), COMMIT("commit");

    private final String wireName;                         // the exact word written in the header

    ObjectType(String wireName) { this.wireName = wireName; }

    public String wireName() { return wireName; }

    public static ObjectType fromWireName(String s) {      // header word -> enum, used when reading
        for (ObjectType t : values()) if (t.wireName.equals(s)) return t;
        throw new IllegalStateException("unknown object type: " + s);
    }
}