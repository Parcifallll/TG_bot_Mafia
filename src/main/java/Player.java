public abstract class Player {
    protected final long userId;
    protected final String username;
    protected Role role;
    protected boolean isAlive = true;

    public Player(long userId, String username) {
        this.userId = userId;
        this.username = username;
    }

    public abstract void performNightAction(Player target);
    public abstract String getRoleDescription();

    public boolean isAlive() { return isAlive; }
    public void setAlive(boolean alive) { isAlive = alive; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public String getUsername() { return username; }
    public long getUserId() { return userId; }
}