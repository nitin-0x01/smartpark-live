import java.time.LocalDateTime;

// Ek parking ka poora bill (parking charge + EV charging charge)
public class Bill {
    public int ticketId;
    public String regNo, owner, typeName, slotNumber, status;
    public LocalDateTime entry, exit;
    public long minutes, hours;
    public double rate, parking, ev, total;
    public boolean paid;
    public String mode, upiTxn;
}
