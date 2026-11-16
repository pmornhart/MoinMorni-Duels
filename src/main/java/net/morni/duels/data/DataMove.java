package net.morni.duels.data;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Umzug der Daten in einen neuen Plugin-Ordner (das Plugin hieß bis zur Umbenennung in morni.net anders, sein Ordner
 * unter {@code plugins/} also auch). Nichts geht verloren: Gibt es den neuen Ordner noch nicht, wird der alte nur
 * umbenannt. Gibt es ihn schon (z. B. weil das Netzwerk die config.yml vorab geschrieben hat), kommt alles aus dem
 * alten Ordner dazu; was es im neuen schon gibt, bleibt dort, und die alte Fassung landet in {@value #KEPT}.
 * Danach ist der alte Ordner weg – beim nächsten Start passiert nichts mehr.
 */
public final class DataMove {

    /** Unterordner im neuen Ordner für alte Dateien, die es im neuen Ordner schon gab. */
    public static final String KEPT = "vor-umbenennung";

    private DataMove() {
    }

    /**
     * Daten von {@code from} nach {@code to} umziehen.
     *
     * @return true, wenn es etwas umzuziehen gab
     */
    public static boolean run(Path from, Path to) throws IOException {
        if (!Files.isDirectory(from, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        if (Files.notExists(to, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectories(to.toAbsolutePath().getParent());
            Files.move(from, to);
            return true;
        }
        if (!Files.isDirectory(to, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(to + " ist kein Ordner");
        }
        merge(from, to, to.resolve(KEPT));
        Files.delete(from);
        return true;
    }

    /** Alles aus {@code from} nach {@code to}; Doppeltes nach {@code kept}. Danach ist {@code from} leer. */
    private static void merge(Path from, Path to, Path kept) throws IOException {
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(from)) {
            for (Path entry : entries) {
                String name = entry.getFileName().toString();
                Path target = to.resolve(name);
                if (Files.notExists(target, LinkOption.NOFOLLOW_LINKS)) {
                    Files.move(entry, target);
                } else if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)
                        && Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
                    merge(entry, target, kept.resolve(name));
                    Files.delete(entry);
                } else {
                    Files.createDirectories(kept);
                    Files.move(entry, kept.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
