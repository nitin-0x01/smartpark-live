import java.security.SecureRandom;
import java.sql.*;
import java.time.LocalDateTime;

// Online reservation: slot book karna, cancel karna, aur gate par check-in
public class ReservationService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RNG = new SecureRandom();

    public void expireOld() throws SQLException {
        try (Connection con = DBConnection.getConnection(); Statement st = con.createStatement()) {
            st.executeUpdate("UPDATE reservation SET status = 'EXPIRED' WHERE status = 'BOOKED' AND end_time < NOW()");
        }
    }

    // Returns 6-character confirmation code
    public String create(int typeId, String reg, String owner, String phone, LocalDateTime start, int hours)
            throws SQLException {
        LocalDateTime now = LocalDateTime.now();
        if (hours < 1 || hours > 12) throw new IllegalStateException("Duration must be between 1 and 12 hours.");
        if (start.isBefore(now.minusMinutes(5))) throw new IllegalStateException("Reservations cannot be made in the past.");
        if (start.isAfter(now.plusDays(7))) throw new IllegalStateException("Reservations can only be made up to 7 days ahead.");
        Timestamp ts = Timestamp.valueOf(start), te = Timestamp.valueOf(start.plusHours(hours));
        expireOld();
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT 1 FROM reservation WHERE reg_no = ? AND status IN ('BOOKED','CHECKED_IN') "
                      + "AND start_time < ? AND end_time > ?")) {
                    ps.setString(1, reg);
                    ps.setTimestamp(2, te);
                    ps.setTimestamp(3, ts);
                    if (ps.executeQuery().next())
                        throw new IllegalStateException("This vehicle already has a reservation at this time.");
                }
                int slotId;
                // Select a slot without an overlapping reservation; near-term reservations require a free slot.
                String slotSql = "SELECT s.slot_id FROM slot s WHERE s.type_id = ? AND NOT EXISTS ("
                    + "SELECT 1 FROM reservation r WHERE r.slot_id = s.slot_id AND r.status IN ('BOOKED','CHECKED_IN') "
                    + "AND r.start_time < ? AND r.end_time > ?) "
                    + "AND (? > NOW() + INTERVAL 30 MINUTE OR s.is_occupied = 0) ORDER BY s.slot_id LIMIT 1 FOR UPDATE";
                try (PreparedStatement ps = con.prepareStatement(slotSql)) {
                    ps.setInt(1, typeId);
                    ps.setTimestamp(2, te);
                    ps.setTimestamp(3, ts);
                    ps.setTimestamp(4, ts);
                    ResultSet rs = ps.executeQuery();
                    if (!rs.next()) throw new IllegalStateException("No slot is available for this time. Try another time.");
                    slotId = rs.getInt(1);
                }
                String code;
                do { code = randomCode(); } while (codeExists(con, code));
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO reservation (confirm_code, reg_no, owner_name, phone, type_id, slot_id, start_time, end_time) "
                      + "VALUES (?,?,?,?,?,?,?,?)")) {
                    ps.setString(1, code);
                    ps.setString(2, reg);
                    ps.setString(3, owner);
                    ps.setString(4, phone);
                    ps.setInt(5, typeId);
                    ps.setInt(6, slotId);
                    ps.setTimestamp(7, ts);
                    ps.setTimestamp(8, te);
                    ps.executeUpdate();
                }
                con.commit();
                return code;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    public void cancel(String code) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "UPDATE reservation SET status = 'CANCELLED' WHERE confirm_code = ? AND status = 'BOOKED'")) {
            ps.setString(1, code);
            if (ps.executeUpdate() == 0) throw new IllegalStateException("This reservation cannot be cancelled; the code is invalid or already used.");
        }
    }

    // Gate check-in creates a ticket and returns its ID.
    public int checkIn(String code) throws SQLException {
        expireOld();
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                int resId, typeId, slotId;
                String reg, owner, phone;
                LocalDateTime start, end;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT res_id, reg_no, owner_name, phone, type_id, slot_id, start_time, end_time "
                      + "FROM reservation WHERE confirm_code = ? AND status = 'BOOKED' FOR UPDATE")) {
                    ps.setString(1, code);
                    ResultSet rs = ps.executeQuery();
                    if (!rs.next()) throw new IllegalStateException("This code does not belong to an active reservation.");
                    resId = rs.getInt(1); reg = rs.getString(2); owner = rs.getString(3); phone = rs.getString(4);
                    typeId = rs.getInt(5); slotId = rs.getInt(6);
                    start = rs.getTimestamp(7).toLocalDateTime();
                    end = rs.getTimestamp(8).toLocalDateTime();
                }
                LocalDateTime now = LocalDateTime.now();
                if (now.isBefore(start.minusMinutes(15)))
                    throw new IllegalStateException("Check-in is not open yet. Arrive 15 minutes before the reservation starts.");
                if (now.isAfter(end)) throw new IllegalStateException("The reservation window has ended.");

                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT 1 FROM ticket t JOIN vehicle v ON t.vehicle_id = v.vehicle_id "
                      + "WHERE v.reg_no = ? AND t.status = 'ACTIVE'")) {
                    ps.setString(1, reg);
                    if (ps.executeQuery().next()) throw new IllegalStateException("This vehicle is already parked.");
                }

                boolean occupied;
                try (PreparedStatement ps = con.prepareStatement("SELECT is_occupied FROM slot WHERE slot_id = ? FOR UPDATE")) {
                    ps.setInt(1, slotId);
                    ResultSet rs = ps.executeQuery();
                    rs.next();
                    occupied = rs.getInt(1) == 1;
                }
                if (occupied) { // If the reserved slot is occupied, find another slot of the same type.
                    try (PreparedStatement ps = con.prepareStatement(
                            "SELECT slot_id FROM slot WHERE type_id = ? AND is_occupied = 0 AND slot_id NOT IN ("
                          + "SELECT slot_id FROM reservation WHERE status = 'BOOKED' AND res_id <> ? "
                          + "AND start_time < NOW() + INTERVAL 60 MINUTE AND end_time > NOW()) ORDER BY slot_id LIMIT 1 FOR UPDATE")) {
                        ps.setInt(1, typeId);
                        ps.setInt(2, resId);
                        ResultSet rs = ps.executeQuery();
                        if (!rs.next()) throw new IllegalStateException("No slot is currently available. Please wait.");
                        slotId = rs.getInt(1);
                    }
                }

                int vehicleId = ParkingService.findOrCreateVehicle(con, reg, owner, phone, typeId);
                int ticketId;
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO ticket (vehicle_id, slot_id, entry_time, status) VALUES (?,?,NOW(),'ACTIVE')",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, vehicleId);
                    ps.setInt(2, slotId);
                    ps.executeUpdate();
                    ResultSet keys = ps.getGeneratedKeys();
                    keys.next();
                    ticketId = keys.getInt(1);
                }
                try (PreparedStatement ps = con.prepareStatement("UPDATE slot SET is_occupied = 1 WHERE slot_id = ?")) {
                    ps.setInt(1, slotId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE reservation SET status = 'CHECKED_IN', ticket_id = ?, slot_id = ? WHERE res_id = ?")) {
                    ps.setInt(1, ticketId);
                    ps.setInt(2, slotId);
                    ps.setInt(3, resId);
                    ps.executeUpdate();
                }
                con.commit();
                return ticketId;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    private static boolean codeExists(Connection con, String code) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT 1 FROM reservation WHERE confirm_code = ?")) {
            ps.setString(1, code);
            return ps.executeQuery().next();
        }
    }

    private static String randomCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) sb.append(ALPHABET.charAt(RNG.nextInt(ALPHABET.length())));
        return sb.toString();
    }
}
