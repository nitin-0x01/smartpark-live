public class Bike extends Vehicle {
    public Bike(String regNo, String ownerName, String phone) {
        super(regNo, ownerName, phone);
    }
    @Override public int getTypeId() { return 2; }
    @Override public String getTypeName() { return "Bike"; }
}
