// Custom exception raised when no slot is available.
public class SlotFullException extends Exception {
    public SlotFullException(String message) {
        super(message);
    }
}
