package de.pbma.moa.fitnessapp;

/**
 * Application interface: I sincerely hope, that at least that interface changes
 * for your application. However, I am afraid that even the comment stays the same.
 */
public interface PressListener {
    void onPress(String id, String text);
    void onMQTTStatus(boolean connected);
    void onLogMessage(String message);
}
