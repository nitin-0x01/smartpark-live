// Application settings.
public class Config {
    public static final int PORT = 8080;

    // URL embedded in QR codes. Use the computer's LAN address for phone scanning.
    // "localhost" ki jagah apne PC ka IP daalo, jaise "http://192.168.1.5:8080"
    public static final String BASE_URL = "http://localhost:" + PORT;

    // UPI payment QR configuration.
    public static final String UPI_VPA = "yourname@upi";
    public static final String UPI_NAME = "City Parking";

    // Secret used to make QR tickets tamper-resistant.
    public static final String SECRET = "change-this-secret-123";
}
