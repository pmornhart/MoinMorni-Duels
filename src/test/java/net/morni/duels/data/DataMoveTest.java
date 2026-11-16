package net.morni.duels.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataMoveTest {

    @TempDir
    Path plugins;

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    @Test
    void nothingToMove() throws IOException {
        Path to = plugins.resolve("Neu");
        assertFalse(DataMove.run(plugins.resolve("Alt"), to));
        assertFalse(Files.exists(to));
    }

    @Test
    void oldFolderIsRenamedWhenTheNewOneIsMissing() throws IOException {
        Path from = plugins.resolve("Alt");
        Path to = plugins.resolve("Neu");
        write(from.resolve("config.yml"), "welt: duels\n");
        write(from.resolve("spieler/a.yml"), "name: Anna\n");
        write(from.resolve("rangliste.yml"), "klassisch: []\n");

        assertTrue(DataMove.run(from, to));

        assertFalse(Files.exists(from));
        assertEquals("welt: duels\n", read(to.resolve("config.yml")));
        assertEquals("name: Anna\n", read(to.resolve("spieler/a.yml")));
        assertEquals("klassisch: []\n", read(to.resolve("rangliste.yml")));
        assertFalse(DataMove.run(from, to), "beim zweiten Start gibt es nichts mehr umzuziehen");
    }

    @Test
    void mergesIntoAnExistingFolderWithoutLosingAnything() throws IOException {
        Path from = plugins.resolve("Alt");
        Path to = plugins.resolve("Neu");
        write(from.resolve("config.yml"), "alt\n");
        write(from.resolve("rangliste.yml"), "liste\n");
        write(from.resolve("spieler/a.yml"), "Anna alt\n");
        write(from.resolve("spieler/b.yml"), "Bert\n");
        write(to.resolve("config.yml"), "neu\n");
        write(to.resolve("spieler/a.yml"), "Anna neu\n");

        assertTrue(DataMove.run(from, to));

        assertFalse(Files.exists(from), "alter Ordner ist weg");
        assertEquals("neu\n", read(to.resolve("config.yml")), "was es schon gab, bleibt");
        assertEquals("liste\n", read(to.resolve("rangliste.yml")));
        assertEquals("Bert\n", read(to.resolve("spieler/b.yml")));
        assertEquals("Anna neu\n", read(to.resolve("spieler/a.yml")));
        assertEquals("alt\n", read(to.resolve(DataMove.KEPT).resolve("config.yml")), "alte Fassung aufgehoben");
        assertEquals("Anna alt\n", read(to.resolve(DataMove.KEPT).resolve("spieler/a.yml")));
    }
}
