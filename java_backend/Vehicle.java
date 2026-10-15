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

    public abstract int getTypeId();
    public abstract String getTypeName();
}
