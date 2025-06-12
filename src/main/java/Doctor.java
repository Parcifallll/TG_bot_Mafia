public class Doctor extends Player {
    public Doctor(long userId, String username) {
        super(userId, username);
        role = Role.DOCTOR;
    }

    @Override
    public void performNightAction(Player target) {
    }

    @Override
    public String getRoleDescription() {
        return "Вы доктор. Спасайте игроков (включая себя) от мафии!";
    }
}