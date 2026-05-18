package com.cringe.player;

/**
 * Точка входа для jpackage.
 * jpackage не может использовать класс, наследующий Application,
 * напрямую как main-class. Этот класс-обёртка решает проблему.
 */
public class Launcher {
    public static void main(String[] args) {
        CringePlayerApp.main(args);
    }
}
