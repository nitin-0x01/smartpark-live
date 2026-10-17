public class Car extends Vehicle {
    public Car(String regNo, String ownerName, String phone) {
        super(regNo, ownerName, phone);
    }
    @Override public int getTypeId() { return 1; }
    @Override public String getTypeName() { return "Car"; }
}
