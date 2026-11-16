package net.morni.duels.world;

/** Die Arenen – alle im Alpen-Stil, eigene Entwürfe. */
public enum ArenaType {
    ALMWIESE("almwiese"),
    BURGHOF("burghof"),
    BERGWERK("bergwerk"),
    WIRTSHAUSGARTEN("wirtshausgarten");

    private final String id;

    ArenaType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** Text-Schlüssel des Namens. */
    public String textKey() {
        return "arena." + id;
    }
}
