public class Commissar extends Player {
    private Player checkedPlayer;

    public Commissar(long userId, String username) {
        super(userId, username);
        this.role = Role.COMMISSAR;
    }

    @Override
    public void performNightAction(Player target) {
        if (target != null && target.isAlive()) {
            checkedPlayer = target;
        }
    }

    public String getCheckResult() {
        if (checkedPlayer == null) {
            return "🔍 Проверка не выполнена";
        }
        return checkedPlayer.getRole() == Role.MAFIA
                ? "🔴 " + checkedPlayer.getUsername() + " — мафия!"
                : "🟢 " + checkedPlayer.getUsername() + " — мирный";
    }

    @Override
    public String getRoleDescription() {
        return """
            🕵️♂️ Вы — комиссар!
            Каждую ночь вы можете проверить одного игрока.
            Используйте команду: /check [имя_игрока]
            """;
    }
}