package ru.reweu.game.loader;

/**
 * Сообщения о проблемах с загрузкой/декодированием текстур (stderr).
 */
public final class TextureLoadLog {

    private static final String PREFIX = "[Texture] ";

    private TextureLoadLog() {
    }

    public static void warn(String message) {
        System.err.println(PREFIX + message);
    }

    public static void info(String message) {
        System.err.println(PREFIX + message);
    }
}
