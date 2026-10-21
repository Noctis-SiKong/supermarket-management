package nz.ac.aut.pdc.infrastructure;

import java.io.IOException;
import java.nio.file.*;

/** Explicit application bootstrap root, validated using repository markers, never user CWD. */
public final class DataRoot {
    private final Path root;
    private DataRoot(Path root) { this.root = root; }
    public static DataRoot forProject(Path project) throws IOException {
        Path real = project.toRealPath();
        if (!Files.isRegularFile(real.resolve("pom.xml")) || !Files.isRegularFile(real.resolve("README.md"))
            || !Files.isDirectory(real.resolve("docs"))) throw new IOException("Project root markers missing");
        Path path = real.resolve("data");
        Files.createDirectories(path);
        if (!path.toRealPath().startsWith(real) || !Files.isWritable(path)) throw new IOException("DATA_ROOT unavailable");
        return new DataRoot(path.toRealPath());
    }
    /** Test fixtures must have their own project markers and temporary root. */
    public Path path() { return root; }
    public Path resolve(String reference) throws IOException {
        if (reference == null || reference.isBlank() || reference.contains("\\") || reference.contains(":"))
            throw new IOException("Unsafe relative path");
        Path relative = Path.of(reference);
        if (relative.isAbsolute()) throw new IOException("Absolute reference");
        for (String part : reference.split("/", -1))
            if (part.isEmpty() || part.equals(".") || part.equals("..")) throw new IOException("Unsafe segment");
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) throw new IOException("Path escapes DATA_ROOT");
        Path existing = target;
        while (!Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) existing = existing.getParent();
        if (!existing.toRealPath().startsWith(root)) throw new IOException("Link escapes DATA_ROOT");
        return target;
    }
}
