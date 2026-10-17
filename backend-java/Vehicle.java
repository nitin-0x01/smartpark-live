// Abstract class: Car aur Bike isse inherit karte hain (OOP: abstraction + inheritance)
public abstract class Vehicle {
    protected String regNo;
    protected String ownerName;
    protected String phone;

    public Vehicle(String regNo, String ownerName, String phone) {
        this.regNo = regNo;
        this.ownerName = ownerName;
        this.phone = phone;
    }

    public String getRegNo() { return regNo; }
    public String getOwnerName() { return ownerName; }
    public String getPhone() { return phone; }

    public abstract int getTypeId();       // vehicle_type table ka id
    public abstract String getTypeName();
}
