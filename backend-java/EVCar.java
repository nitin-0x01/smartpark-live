public class EVCar extends Vehicle {
    public EVCar(String regNo, String ownerName, String phone) {
        super(regNo, ownerName, phone);
    }
    @Override public int getTypeId() { return 3; }
    @Override public String getTypeName() { return "EV Car"; }
}
