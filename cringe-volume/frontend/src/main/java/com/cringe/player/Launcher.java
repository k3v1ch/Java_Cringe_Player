package com.cringe.player;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Entry point for jpackage.
 * jpackage cannot use an Application subclass as main-class directly.
 * This wrapper also catches fatal errors and writes a crash log,
 * because jpackage exe has no console by default.
 */
public class Launcher {

    public static void main(String[] args) {
        try {
            CringePlayerApp.main(args);
        } catch (Throwable t) {
            handleCrash(t);
        }
    }

    private static void handleCrash(Throwable t) {
        StringWriter sw = new StringWriter();
        sw.write("=== Cringe Volume Player CRASH ===\n");
        sw.write("Time: " + java.time.LocalDateTime.now() + "\n");
        sw.write("Java: " + System.getProperty("java.version") + "\n");
        sw.write("OS: " + System.getProperty("os.name") + " "
                + System.getProperty("os.version") + "\n\n");
        t.printStackTrace(new PrintWriter(sw));

        String crashText = sw.toString();
        System.err.println(crashText);

        // Write crash log next to the exe (or in user.dir)
        try {
            Path logFile = Path.of(System.getProperty("user.dir"), "cringe-crash.log");
            Files.writeString(logFile, crashText);
        } catch (Exception ignored) {
            // If we can't write to user.dir, try user.home
            try {
                Path logFile = Path.of(System.getProperty("user.home"), "cringe-crash.log");
                Files.writeString(logFile, crashText);
            } catch (Exception alsoIgnored) {
                // nothing we can do
            }
        }

        // Try to show a Swing dialog (java.desktop is available via JavaFX deps)
        try {
            javax.swing.JOptionPane.showMessageDialog(
                    null,
                    "Cringe Volume Player crashed!\n\n"
                            + t.getClass().getSimpleName() + ": " + t.getMessage()
                            + "\n\nSee cringe-crash.log for details.",
                    "Crash",
                    javax.swing.JOptionPane.ERROR_MESSAGE
            );
        } catch (Throwable uiError) {
            // headless or no AWT — already wrote log, just exit
        }

        System.exit(1);
    }
}
