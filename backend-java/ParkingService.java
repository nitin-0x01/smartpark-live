import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ParkingService {

    // ---------- ENTRY: vehicle park karna ----------
    public int parkVehicle(Vehicle v) throws SQLException, SlotFullException {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false); // transaction start
            try {
                // 1. Check whether the vehicle is already parked.
                String chkSql = "SELECT 1 FROM ticket t JOIN vehicle ve ON t.vehicle_id = ve.vehicle_id "
                              + "WHERE ve.reg_no = ? AND t.status = 'ACTIVE'";
                try (PreparedStatement ps = con.prepareStatement(chkSql)) {
                    ps.setString(1, v.getRegNo());
                    if (ps.executeQuery().next()) {
                        throw new IllegalStateException("This vehicle is already parked.");
                    }
                }

                // 2. Find an available slot for this vehicle type.
                int slotId;
                String slotNo;
                String slotSql = "SELECT slot_id, slot_number FROM slot "
                               + "WHERE type_id = ? AND is_occupied = 0 AND slot_id NOT IN ("
                               + "SELECT slot_id FROM reservation WHERE status = 'BOOKED' "
                               + "AND start_time < NOW() + INTERVAL 60 MINUTE AND end_time > NOW()) "
                               + "ORDER BY slot_id LIMIT 1 FOR UPDATE";
                try (PreparedStatement ps = con.prepareStatement(slotSql)) {
                    ps.setInt(1, v.getTypeId());
                    ResultSet rs = ps.executeQuery();
                    if (!rs.next()) {
                        throw new SlotFullException("No available slot for " + v.getTypeName() + ".");
                    }
                    slotId = rs.getInt("slot_id");
                    slotNo = rs.getString("slot_number");
                }

                // 3. Find the vehicle or insert it when it does not exist.
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

                // 4. Create the ticket.
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

                // 5. Mark the slot as occupied.
                try (PreparedStatement ps = con.prepareStatement("UPDATE slot SET is_occupied = 1 WHERE slot_id = ?")) {
                    ps.setInt(1, slotId);
                    ps.executeUpdate();
                }

                con.commit(); // Transaction completed successfully.
                System.out.println("Vehicle parked! Slot: " + slotNo + " | Ticket ID: " + ticketId);
                return ticketId;
            } catch (Exception e) {
                con.rollback(); // Undo the transaction after an error.
                throw e;
            }
        }
    }

    // ---------- EXIT: vehicle and payment ----------
    public double exitVehicle(String regNo, String mode) throws SQLException {
        return exitVehicle(regNo, mode, null).total;
    }

    // Returns the complete bill (parking + EV charging) and stores the UPI transaction ID.
    public Bill exitVehicle(String regNo, String mode, String upiTxn) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                int ticketId, slotId;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT t.ticket_id, t.slot_id FROM ticket t JOIN vehicle v ON t.vehicle_id = v.vehicle_id "
                      + "WHERE v.reg_no = ? AND t.status = 'ACTIVE' FOR UPDATE")) {
                    ps.setString(1, regNo);
                    ResultSet rs = ps.executeQuery();
                    if (!rs.next()) throw new IllegalStateException("No active ticket was found for this vehicle.");
                    ticketId = rs.getInt(1);
                    slotId = rs.getInt(2);
                }

                EvService.stopActive(con, ticketId, null); // Stop any active EV charging session.

                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE ticket SET exit_time = NOW(), status = 'COMPLETED' WHERE ticket_id = ?")) {
                    ps.setInt(1, ticketId);
                    ps.executeUpdate();
                }
                Bill b = loadBill(con, ticketId);   // automatic bill

                try (PreparedStatement ps = con.prepareStatement("UPDATE slot SET is_occupied = 0 WHERE slot_id = ?")) {
                    ps.setInt(1, slotId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE reservation SET status = 'COMPLETED' WHERE ticket_id = ? AND status = 'CHECKED_IN'")) {
                    ps.setInt(1, ticketId);
                    ps.executeUpdate();
                }
                int paymentId;
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO payment (ticket_id, amount, pay_time, mode) VALUES (?,?,NOW(),?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, ticketId);
                    ps.setDouble(2, b.total);
                    ps.setString(3, mode);
                    ps.executeUpdate();
                    ResultSet keys = ps.getGeneratedKeys();
                    keys.next();
                    paymentId = keys.getInt(1);
                }
                if ("UPI".equals(mode) && upiTxn != null) {
                    try (PreparedStatement ps = con.prepareStatement(
                            "INSERT INTO upi_payment (payment_id, txn_ref, vpa) VALUES (?,?,?)")) {
                        ps.setInt(1, paymentId);
                        ps.setString(2, upiTxn);
                        ps.setString(3, Config.UPI_VPA);
                        ps.executeUpdate();
                    }
                }
                con.commit();
                b.paid = true;
                b.mode = mode;
                b.upiTxn = upiTxn;
                System.out.println("Exit done. Ghante: " + b.hours + " | Amount: Rs. " + b.total);
                return b;
            } catch (SQLException e) {
                con.rollback();
                if (e.getErrorCode() == 1062 && upiTxn != null)
                    throw new IllegalStateException("This UPI transaction ID has already been used.");
                throw e;
            } catch (RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    // Preview a bill without changing data.
    public Bill previewBill(String regNo) throws SQLException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                 "SELECT t.ticket_id FROM ticket t JOIN vehicle v ON t.vehicle_id = v.vehicle_id "
               + "WHERE v.reg_no = ? AND t.status = 'ACTIVE'")) {
            ps.setString(1, regNo);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) throw new IllegalStateException("No active ticket was found for this vehicle.");
            return loadBill(con, rs.getInt(1));
        }
    }

    public Bill getBill(int ticketId) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return loadBill(con, ticketId);
        }
    }

    // Fee = rounded-up hours (minimum 1) x rate + EV charging.
    static Bill loadBill(Connection con, int ticketId) throws SQLException {
        Bill b = new Bill();
        Timestamp exitTs;
        String sql = "SELECT t.ticket_id, t.entry_time, t.exit_time, t.status, v.reg_no, v.owner_name, "
                   + "vt.type_name, vt.rate_per_hour, s.slot_number FROM ticket t "
                   + "JOIN vehicle v ON t.vehicle_id = v.vehicle_id "
                   + "JOIN vehicle_type vt ON v.type_id = vt.type_id "
                   + "JOIN slot s ON t.slot_id = s.slot_id WHERE t.ticket_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, ticketId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) throw new IllegalStateException("Ticket not found.");
            b.ticketId = rs.getInt("ticket_id");
            b.entry = rs.getTimestamp("entry_time").toLocalDateTime();
            exitTs = rs.getTimestamp("exit_time");
            b.status = rs.getString("status");
            b.regNo = rs.getString("reg_no");
            b.owner = rs.getString("owner_name");
            b.typeName = rs.getString("type_name");
            b.rate = rs.getDouble("rate_per_hour");
            b.slotNumber = rs.getString("slot_number");
        }
        LocalDateTime end = (exitTs != null) ? exitTs.toLocalDateTime() : LocalDateTime.now();
        b.exit = (exitTs != null) ? end : null;
        b.minutes = Duration.between(b.entry, end).toMinutes();
        b.hours = Math.max(1, (long) Math.ceil(b.minutes / 60.0));
        b.parking = b.hours * b.rate;
        b.ev = EvService.estimate(con, ticketId);
        b.total = EvService.round2(b.parking + b.ev);
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT p.amount, p.mode, u.txn_ref FROM payment p LEFT JOIN upi_payment u ON u.payment_id = p.payment_id "
              + "WHERE p.ticket_id = ?")) {
            ps.setInt(1, ticketId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                b.total = rs.getDouble(1);
                b.mode = rs.getString(2);
                b.upiTxn = rs.getString(3);
                b.paid = true;
            }
        }
        return b;
    }

    // Find the vehicle or insert it when it does not exist.
    static int findOrCreateVehicle(Connection con, String reg, String owner, String phone, int typeId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT vehicle_id FROM vehicle WHERE reg_no = ?")) {
            ps.setString(1, reg);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        }
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO vehicle (reg_no, owner_name, phone, type_id) VALUES (?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, reg);
            ps.setString(2, owner);
            ps.setString(3, phone);
            ps.setInt(4, typeId);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            keys.next();
            return keys.getInt(1);
        }
    }

    // ---------- REPORTS ----------
    public void showActiveParking() throws SQLException {
        String sql = "SELECT t.ticket_id, v.reg_no, v.owner_name, vt.type_name, s.slot_number, t.entry_time "
                   + "FROM ticket t "
                   + "JOIN vehicle v ON t.vehicle_id = v.vehicle_id "
                   + "JOIN vehicle_type vt ON v.type_id = vt.type_id "
                   + "JOIN slot s ON t.slot_id = s.slot_id "
                   + "WHERE t.status = 'ACTIVE'";
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            System.out.println("\nTicket | Reg No | Owner | Type | Slot | Entry Time");
            boolean any = false;
            while (rs.next()) {
                any = true;
                System.out.println(rs.getInt(1) + " | " + rs.getString(2) + " | " + rs.getString(3) + " | "
                        + rs.getString(4) + " | " + rs.getString(5) + " | " + rs.getTimestamp(6));
            }
            if (!any) System.out.println("No vehicles are currently parked.");
        }
    }

    public void showAvailableSlots() throws SQLException {
        String sql = "SELECT vt.type_name, COUNT(*) AS free_slots FROM slot s "
                   + "JOIN vehicle_type vt ON s.type_id = vt.type_id "
                   + "WHERE s.is_occupied = 0 GROUP BY vt.type_name";
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            System.out.println("\nKhali slots:");
            while (rs.next()) {
                System.out.println(rs.getString("type_name") + " : " + rs.getInt("free_slots"));
            }
        }
    }

    public void showTodayCollection() throws SQLException {
        String sql = "SELECT IFNULL(SUM(amount),0) AS total, COUNT(*) AS cnt FROM payment "
                   + "WHERE DATE(pay_time) = CURDATE()";
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                System.out.println("\nAaj ki collection: Rs. " + rs.getDouble("total")
                        + " (" + rs.getInt("cnt") + " payments)");
            }
        }
    }

    public void showVehicleHistory(String regNo) throws SQLException {
        String sql = "SELECT t.ticket_id, t.entry_time, t.exit_time, t.status, IFNULL(p.amount,0) AS amount "
                   + "FROM ticket t JOIN vehicle v ON t.vehicle_id = v.vehicle_id "
                   + "LEFT JOIN payment p ON p.ticket_id = t.ticket_id "
                   + "WHERE v.reg_no = ? ORDER BY t.entry_time DESC";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, regNo);
            ResultSet rs = ps.executeQuery();
            System.out.println("\nTicket | Entry | Exit | Status | Amount");
            boolean any = false;
            while (rs.next()) {
                any = true;
                System.out.println(rs.getInt(1) + " | " + rs.getTimestamp(2) + " | " + rs.getTimestamp(3)
                        + " | " + rs.getString(4) + " | Rs. " + rs.getDouble(5));
            }
            if (!any) System.out.println("No records found for this vehicle.");
        }
    }

    // ---------- WEB PAGE: run a query and return rows (first row contains column names) ----------
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
                    String txt = (o == null) ? "-" : String.valueOf(o);
                    if (o instanceof LocalDateTime) txt = txt.replace('T', ' ');
                    else if (o instanceof Timestamp && txt.endsWith(".0")) txt = txt.substring(0, txt.length() - 2);
                    row[i] = txt;
                }
                rows.add(row);
            }
        }
        return rows;
    }
}
