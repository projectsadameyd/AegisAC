package dev.aegisac.core;

public enum CheckType {
    SPEED("Speed", "A"),
    FLY("Fly", "A"),
    TIMER("Timer", "A"),
    REACH("Reach", "A"),
    AUTOCLICKER("AutoClicker", "A"),
    FASTPLACE("FastPlace", "A"),
    FREECAM_INTERACT("RemoteInteract", "A"),
    VELOCITY("Velocity", "A");

    private final String display;
    private final String variant;

    CheckType(String display, String variant) {
        this.display = display;
        this.variant = variant;
    }

    public String display() { return display; }
    public String variant() { return variant; }
}
