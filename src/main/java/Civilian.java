public class Civilian extends Player {
    public Civilian(long userId, String username) {
        super(userId, username);
        this.role = Role.CIVILIAN;
    }

    @Override
    public void performNightAction(Player target) {
    }

    @Override
    public String getRoleDescription() {
        return "Вы мирный житель. Ваша задача — вычислить мафию и проголосовать за её ликвидацию.";
    }
}