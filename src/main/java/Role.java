public enum Role {
    CIVILIAN("Civilian"),
    MAFIA("Mafia"),
    DOCTOR("Doctor"),
    COMMISSAR("Commissar");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}