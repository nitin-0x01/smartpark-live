import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ParkingService {

    // ---------- ENTRY: Vehicle park karna ----------
    public int parkVehicle(Vehicle v) throws SQLException, SlotFullException {
        return parkVehicle(v, null);
    }

    public int parkVehicle(Vehicle v, Integer requestedSlotId) throws SQLException, SlotFullException {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                // 1. Check if vehicle is already parked
                String chkSql = "SELECT 1 FROM ticket t JOIN vehicle ve ON t.vehicle_id = ve.vehicle_id "
                              + "WHERE ve.reg_no = ? AND t.status = 'ACTIVE'";
                try (PreparedStatement ps = con.prepareStatement(chkSql)) {
                    ps.setString(1, v.getRegNo());
                    if (ps.executeQuery().next()) {
                        throw new IllegalStateException("Ye vehicle already parked hai!");
                    }
                }

                // 2. Find available slot for vehicle type with row lock
                int slotId;
                String slotNo;
                String slotSql = requestedSlotId == null
                    ? "SELECT slot_id, slot_number FROM slot WHERE type_id = ? AND is_occupied = 0 LIMIT 1 FOR UPDATE"
                    : "SELECT slot_id, slot_number FROM slot WHERE slot_id = ? AND type_id = ? AND is_occupied = 0 FOR UPDATE";
                try (PreparedStatement ps = con.prepareStatement(slotSql)) {
                    if (requestedSlotId == null) {
                        ps.setInt(1, v.getTypeId());
                    } else {
                        ps.setInt(1, requestedSlotId);
                        ps.setInt(2, v.getTypeId());
                    }
                    ResultSet rs = ps.executeQuery();
                    if (!rs.next()) {
                        throw new SlotFullException("Sorry, " + v.getTypeName() + " ke liye koi slot khali nahi hai!");
                    }
                    slotId = rs.getInt("slot_id");
                    slotNo = rs.getString("slot_number");
                }

                // 3. Register or fetch vehicle
                int vehicleId;
                try (PreparedStatement ps = con.prepareStatement("SELECT vehicle_id FROM vehicle WHERE reg_no = ?")) {
                    ps.setString(1, v.getRegNo());
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        vehicleId = rs.getInt(1);
                    } else {
                        String ins = "INSERT INTO vehicle (reg_no, owner_name, phone, type_id) VALUES (?,?,?,?)";
                        try (PreparedStatement ps2 = con.prepareStatement(ins, Statement.RETURN_GENERATED_KEYS)) {
                            ps2.setString(1, v.getRegNo());
                            ps2.setString(2, v.getOwnerName());
                            ps2.setString(3, v.getPhone());
                            ps2.setInt(4, v.getTypeId());
                            ps2.executeUpdate();
                            ResultSet keys = ps2.getGeneratedKeys();
                            keys.next();
                            vehicleId = keys.getInt(1);
                        }
                    }
                }

                // 4. Create active ticket
                int ticketId;
                String tSql = "INSERT INTO ticket (vehicle_id, slot_id, entry_time, status) VALUES (?,?,NOW(),'ACTIVE')";
                try (PreparedStatement ps = con.prepareStatement(tSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, vehicleId);
                    ps.setInt(2, slotId);
                    ps.executeUpdate();
                    ResultSet keys = ps.getGeneratedKeys();
                    keys.next();
                    ticketId = keys.getInt(1);
                }

                // 5. Update slot to occupied
                try (PreparedStatement ps = con.prepareStatement("UPDATE slot SET is_occupied = 1, status = 'OCCUPIED' WHERE slot_id = ?")) {
                    ps.setInt(1, slotId);
                    ps.executeUpdate();
                }

                con.commit();
                System.out.println("Vehicle parked! Slot: " + slotNo + " | Ticket ID: " + ticketId);
                return ticketId;
            } catch (Exception e) {
                con.rollback();
                throw e;
            }
        }
    }

    // ---------- EXIT: Automatic Billing Calculation ----------
    public double exitVehicle(String regNo, String mode) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                String sql = "SELECT t.ticket_id, t.slot_id, t.entry_time, vt.rate_per_hour "
                           + "FROM ticket t "
                           + "JOIN vehicle v ON t.vehicle_id = v.vehicle_id "
                           + "JOIN vehicle_type vt ON v.type_id = vt.type_id "
                           + "WHERE v.reg_no = ? AND t.status = 'ACTIVE'";
                int ticketId, slotId;
                double rate;
                LocalDateTime entry;
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setString(1, regNo);
                    ResultSet rs = ps.executeQuery();
                    if (!rs.next()) {
                        throw new IllegalStateException("Is vehicle ka koi active ticket nahi mila!");
                    }
                    ticketId = rs.getInt("ticket_id");
                    slotId = rs.getInt("slot_id");
                    rate = rs.getDouble("rate_per_hour");
                    entry = rs.getTimestamp("entry_time").toLocalDateTime();
                }

                // Duration calculation (Minimum 1 hr, rounded up)
                long minutes = Duration.between(entry, LocalDateTime.now()).toMinutes();
                long hours = Math.max(1, (long) Math.ceil(minutes / 60.0));
                double amount = hours * rate;

                // Complete ticket
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE ticket SET exit_time = NOW(), status = 'COMPLETED' WHERE ticket_id = ?")) {
                    ps.setInt(1, ticketId);
                    ps.executeUpdate();
                }

                // Free slot
                try (PreparedStatement ps = con.prepareStatement("UPDATE slot SET is_occupied = 0, status = 'AVAILABLE' WHERE slot_id = ?")) {
                    ps.setInt(1, slotId);
                    ps.executeUpdate();
                }

                // Record payment
                String txnRef = mode.toUpperCase() + System.currentTimeMillis();
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO payment (ticket_id, amount, pay_time, mode, transaction_ref) VALUES (?,?,NOW(),?,?)")) {
                    ps.setInt(1, ticketId);
                    ps.setDouble(2, amount);
                    ps.setString(3, mode);
                    ps.setString(4, txnRef);
                    ps.executeUpdate();
                }

                con.commit();
                System.out.println("Exit done. Ghante: " + hours + " | Amount: Rs. " + amount);
                return amount;
            } catch (Exception e) {
                con.rollback();
                throw e;
            }
        }
    }

    // ---------- QUERY HELPER ----------
    public List<String[]> query(String sql, String... params) throws SQLException {
        List<String[]> rows = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            ResultSet rs = ps.executeQuery();
            ResultSetMetaData md = rs.getMetaData();
            int cols = md.getColumnCount();
            String[] header = new String[cols];
            for (int i = 0; i < cols; i++) header[i] = md.getColumnLabel(i + 1).replace('_', ' ');
            rows.add(header);
            while (rs.next()) {
                String[] row = new String[cols];
                for (int i = 0; i < cols; i++) {
                    Object o = rs.getObject(i + 1);
                    row[i] = (o == null) ? "-" : String.valueOf(o);
                }
                rows.add(row);
            }
        }
        return rows;
    }
}
