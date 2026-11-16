package de.moinmorni.duels.data;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Daten aus einem anderen Plugin-Ordner zurückholen: Duels hieß in Version 0.1.1 kurz anders (Plugin
 * {@code MorniDuels}), seine Daten lagen also in {@code plugins/MorniDuels}. Seit 0.1.2 heißt es wieder
 * {@code MoinMorniDuels} – dieser Umzug bringt die Daten zurück, und nichts geht verloren: Gibt es den Zielordner noch
 * nicht, wird der andere nur umbenannt. Gibt es ihn schon (z. B. weil das Netzwerk die config.yml vorab geschrieben
 * hat), kommt alles aus dem anderen Ordner dazu; was es im Ziel schon gibt, bleibt dort, und die andere Fassung landet
 * in {@value #KEPT}. Danach ist der andere Ordner weg – beim nächsten Start passiert nichts mehr.
 */
public final class DataMove {

    /** Unterordner im Ziel für Dateien aus 0.1.1, die es im Ziel schon gab. */
    public static final String KEPT = "aus-0.1.1";

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
