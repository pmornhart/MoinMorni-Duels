package de.moinmorni.duels.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Rückumzug aus dem Ordner der Version 0.1.1 ({@code plugins/MorniDuels}) nach {@code plugins/MoinMorniDuels}. */
class DataMoveTest {

    @TempDir
    Path plugins;

    private Path from() {
        return plugins.resolve("MorniDuels");
    }

    private Path to() {
        return plugins.resolve("MoinMorniDuels");
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    @Test
    void nothingToMove() throws IOException {
        assertFalse(DataMove.run(from(), to()));
        assertFalse(Files.exists(to()));
    }

    @Test
    void folderOf011IsRenamedBackWhenTheOldOneIsMissing() throws IOException {
        write(from().resolve("config.yml"), "welt: duels\n");
        write(from().resolve("bau.yml"), "version: 3\n");
        write(from().resolve("spieler/a.yml"), "name: Anna\n");
        write(from().resolve("rangliste.yml"), "klassisch: []\n");

        assertTrue(DataMove.run(from(), to()));

        assertFalse(Files.exists(from()));
        assertEquals("welt: duels\n", read(to().resolve("config.yml")));
        assertEquals("version: 3\n", read(to().resolve("bau.yml")), "Bau-Stand bleibt – kein Neubau");
        assertEquals("name: Anna\n", read(to().resolve("spieler/a.yml")));
        assertEquals("klassisch: []\n", read(to().resolve("rangliste.yml")));
        assertFalse(DataMove.run(from(), to()), "beim zweiten Start gibt es nichts mehr umzuziehen");
    }

    @Test
    void mergesIntoAnExistingFolderWithoutLosingAnything() throws IOException {
        write(from().resolve("config.yml"), "aus 0.1.1\n");
        write(from().resolve("rangliste.yml"), "liste\n");
        write(from().resolve("spieler/a.yml"), "Anna 0.1.1\n");
        write(from().resolve("spieler/b.yml"), "Bert\n");
        // Hatte 0.1.1 selbst schon etwas zusammengeführt, liegt dort ein eigener Unterordner – er kommt einfach mit.
        write(from().resolve("vor-umbenennung/config.yml"), "aus 0.1.0\n");
        write(to().resolve("config.yml"), "vom Netzwerk\n");
        write(to().resolve("spieler/a.yml"), "Anna neu\n");

        assertTrue(DataMove.run(from(), to()));

        assertFalse(Files.exists(from()), "Ordner aus 0.1.1 ist weg");
        assertEquals("vom Netzwerk\n", read(to().resolve("config.yml")), "was es schon gab, bleibt");
        assertEquals("liste\n", read(to().resolve("rangliste.yml")));
        assertEquals("Bert\n", read(to().resolve("spieler/b.yml")));
        assertEquals("Anna neu\n", read(to().resolve("spieler/a.yml")));
        assertEquals("aus 0.1.1\n", read(to().resolve(DataMove.KEPT).resolve("config.yml")),
                "Fassung aus 0.1.1 aufgehoben");
        assertEquals("Anna 0.1.1\n", read(to().resolve(DataMove.KEPT).resolve("spieler/a.yml")));
        assertEquals("aus 0.1.0\n", read(to().resolve("vor-umbenennung/config.yml")));
        assertFalse(DataMove.run(from(), to()), "beim zweiten Start gibt es nichts mehr umzuziehen");
    }

    @Test
    void refusesWhenTheTargetIsNoFolder() throws IOException {
        write(from().resolve("config.yml"), "welt: duels\n");
        write(to(), "kein Ordner\n");

        assertThrows(IOException.class, () -> DataMove.run(from(), to()));
        assertEquals("welt: duels\n", read(from().resolve("config.yml")), "nichts angefasst");
    }
}
