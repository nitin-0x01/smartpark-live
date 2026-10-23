import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        ParkingService service = new ParkingService();
        Scanner sc = new Scanner(System.in);
        int choice;

        do {
            System.out.println("\n===== PARKING MANAGEMENT SYSTEM =====");
            System.out.println("1. Vehicle Entry (Park)");
            System.out.println("2. Vehicle Exit (Pay & Leave)");
            System.out.println("3. Currently Parked Vehicles");
            System.out.println("4. Available Slots");
            System.out.println("5. Today's Collection");
            System.out.println("6. Vehicle History");
            System.out.println("0. Exit");
            System.out.print("Choice: ");
            choice = Integer.parseInt(sc.nextLine().trim());

            try {
                switch (choice) {
                    case 1:
                        System.out.print("Vehicle type (1 = Car, 2 = Bike, 3 = EV Car): ");
                        int type = Integer.parseInt(sc.nextLine().trim());
                        System.out.print("Registration No: ");
                        String reg = sc.nextLine().trim().toUpperCase();
                        System.out.print("Owner name: ");
                        String owner = sc.nextLine().trim();
                        System.out.print("Phone: ");
                        String phone = sc.nextLine().trim();

                        Vehicle v;  // polymorphism: parent reference, child object
                        if (type == 1) v = new Car(reg, owner, phone);
                        else if (type == 2) v = new Bike(reg, owner, phone);
                        else if (type == 3) v = new EVCar(reg, owner, phone);
                        else { System.out.println("Invalid vehicle type."); break; }
                        service.parkVehicle(v);
                        break;
                    case 2:
                        System.out.print("Registration No: ");
                        String r = sc.nextLine().trim().toUpperCase();
                        System.out.print("Payment mode (Cash/UPI/Card): ");
                        String mode = sc.nextLine().trim();
                        service.exitVehicle(r, mode);
                        break;
                    case 3: service.showActiveParking(); break;
                    case 4: service.showAvailableSlots(); break;
                    case 5: service.showTodayCollection(); break;
                    case 6:
                        System.out.print("Registration No: ");
                        service.showVehicleHistory(sc.nextLine().trim().toUpperCase());
                        break;
                    case 0: System.out.println("Bye!"); break;
                    default: System.out.println("Invalid choice.");
                }
            } catch (SlotFullException e) {
                System.out.println("[SLOT FULL] " + e.getMessage());
            } catch (IllegalStateException e) {
                System.out.println("[ERROR] " + e.getMessage());
            } catch (java.sql.SQLException e) {
                System.out.println("[DB ERROR] " + e.getMessage());
            }
        } while (choice != 0);
        sc.close();
    }
}
