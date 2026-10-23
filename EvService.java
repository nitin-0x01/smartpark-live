import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;

// EV charging: session start/stop aur charge calculation
public class EvService {

    static double round2(double x) { return Math.round(x * 100.0) / 100.0; }

    private int activeTicket(Connection con, String regNo) throws SQLException {
        String sql = "SELECT t.ticket_id FROM ticket t JOIN vehicle v ON t.vehicle_id = v.vehicle_id "
                   + "WHERE v.reg_no = ? AND t.status = 'ACTIVE' FOR UPDATE";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, regNo);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) throw new IllegalStateException("This vehicle is not currently parked.");
            return rs.getInt(1);
        }
    }

    public void start(String regNo) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                int ticketId = activeTicket(con, regNo);
                int chargerId;
                double rate;
                String status;
                String sql = "SELECT c.charger_id, c.rate_per_kwh, c.status FROM ticket t "
                           + "JOIN ev_charger c ON c.slot_id = t.slot_id WHERE t.ticket_id = ? FOR UPDATE";
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setInt(1, ticketId);
                    ResultSet rs = ps.executeQuery();
                    if (!rs.next()) throw new IllegalStateException(
                        "There is no EV charger at this slot. Park the EV in an E1-E3 slot.");
                    chargerId = rs.getInt(1);
                    rate = rs.getDouble(2);
                    status = rs.getString(3);
                }
                if (!"AVAILABLE".equals(status)) {
                    throw new IllegalStateException("The charger is not available (" + status + ").");
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO ev_session (ticket_id, charger_id, start_time, rate_per_kwh, status) "
                      + "VALUES (?, ?, NOW(), ?, 'CHARGING')")) {
                    ps.setInt(1, ticketId);
                    ps.setInt(2, chargerId);
                    ps.setDouble(3, rate);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE ev_charger SET status = 'CHARGING' WHERE charger_id = ?")) {
                    ps.setInt(1, chargerId);
                    ps.executeUpdate();
                }
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    // returns {units, amount} of the stopped session
    public double[] stop(String regNo, Double manualUnits) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                int ticketId = activeTicket(con, regNo);
                double[] r = stopActive(con, ticketId, manualUnits);
                if (r[0] == 0)                 throw new IllegalStateException("This vehicle has no active charging session.");
                con.commit();
                return new double[]{r[1], r[2]};
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    // Stop all active sessions for a ticket. Returns {count, units, amount}.
    static double[] stopActive(Connection con, int ticketId, Double manualUnits) throws SQLException {
        String sql = "SELECT es.session_id, es.charger_id, es.start_time, es.rate_per_kwh, c.power_kw "
                   + "FROM ev_session es JOIN ev_charger c ON es.charger_id = c.charger_id "
                   + "WHERE es.ticket_id = ? AND es.status = 'CHARGING' FOR UPDATE";
        double count = 0, units = 0, amount = 0;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, ticketId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int sessionId = rs.getInt(1), chargerId = rs.getInt(2);
                LocalDateTime start = rs.getTimestamp(3).toLocalDateTime();
                double rate = rs.getDouble(4), power = rs.getDouble(5);
                double hours = Duration.between(start, LocalDateTime.now()).toMillis() / 3600000.0;
                double u = (manualUnits != null) ? manualUnits : round2(power * hours);
                double a = round2(u * rate);
                try (PreparedStatement up = con.prepareStatement(
                        "UPDATE ev_session SET end_time = NOW(), units_kwh = ?, amount = ?, status = 'COMPLETED' "
                      + "WHERE session_id = ?")) {
                    up.setDouble(1, u);
                    up.setDouble(2, a);
                    up.setInt(3, sessionId);
                    up.executeUpdate();
                }
                try (PreparedStatement up = con.prepareStatement(
                        "UPDATE ev_charger SET status = 'AVAILABLE' WHERE charger_id = ? AND status = 'CHARGING'")) {
                    up.setInt(1, chargerId);
                    up.executeUpdate();
                }
                count++; units += u; amount += a;
            }
        }
        return new double[]{count, units, round2(amount)};
    }

    // Total EV charge for a ticket, including an estimate for an active session. Read-only.
    static double estimate(Connection con, int ticketId) throws SQLException {
        String sql = "SELECT es.status, es.amount, es.start_time, es.rate_per_kwh, c.power_kw "
                   + "FROM ev_session es JOIN ev_charger c ON es.charger_id = c.charger_id WHERE es.ticket_id = ?";
        double total = 0;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, ticketId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                if ("COMPLETED".equals(rs.getString(1))) {
                    total += rs.getDouble(2);
                } else {
                    double hours = Duration.between(rs.getTimestamp(3).toLocalDateTime(), LocalDateTime.now()).toMillis() / 3600000.0;
                    total += round2(rs.getDouble(5) * hours * rs.getDouble(4));
                }
            }
        }
        return round2(total);
    }

    // Returns whether this vehicle is charging; charging must stop before checkout.
    public boolean isCharging(String regNo) throws SQLException {
        String sql = "SELECT 1 FROM ev_session es JOIN ticket t ON es.ticket_id = t.ticket_id "
                   + "JOIN vehicle v ON t.vehicle_id = v.vehicle_id "
                   + "WHERE v.reg_no = ? AND t.status = 'ACTIVE' AND es.status = 'CHARGING'";
        try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, regNo);
            return ps.executeQuery().next();
        }
    }

    public void setRate(double rate) throws SQLException {
        if (rate <= 0 || rate > 500) throw new IllegalStateException("Rate must be greater than 0 and no more than 500.");
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE ev_charger SET rate_per_kwh = ?")) {
            ps.setDouble(1, rate);
            ps.executeUpdate();
        }
    }
}
