public class Mafia extends Player {
    public Mafia(long userId, String username) {
        super(userId, username);
        role = Role.MAFIA;
    }

    @Override
    public void performNightAction(Player target) {
    }

    @Override
    public String getRoleDescription() {
        return "Вы мафия! Убивайте мирных жителей ночью.";
    }
}