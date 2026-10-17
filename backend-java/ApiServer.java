import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Locale.US;

/**
 * JSON API companion for the legacy HTML server.
 *
 * The API is additive: it uses the existing services and database queries,
 * while WebServer remains available for the current application.
 */
public class ApiServer {
    private static final int API_PORT = 8081;
    private static final ParkingService service = new ParkingService();
    private static final EvService ev = new EvService();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(API_PORT), 0);
        server.createContext("/api/health", ApiServer::health);
        server.createContext("/api/login", ApiServer::login);
        server.createContext("/api/logout", ApiServer::logout);
        server.createContext("/api/dashboard", ApiServer::dashboard);
        server.createContext("/api/slots", ApiServer::slots);
        server.createContext("/api/park", ApiServer::park);
        server.createContext("/api/checkout", ApiServer::checkout);
        server.createContext("/api/exit", ApiServer::exit);
        server.createContext("/api/ev/start", ApiServer::startCharging);
        server.createContext("/api/ev/stop", ApiServer::stopCharging);
        server.start();
        System.out.println("JSON API started at http://localhost:" + API_PORT);
    }

    private static void health(HttpExchange ex) throws IOException {
        json(ex, 200, "{\"status\":\"ok\",\"service\":\"parking-api\"}");
    }

    private static void login(HttpExchange ex) throws IOException {
        try {
            Map<String, String> form = parse(body(ex));
            String token = Auth.login(form.getOrDefault("username", "").trim(), form.getOrDefault("password", ""));
            if (token == null) {
                json(ex, 401, "{\"error\":\"Incorrect username or password.\"}");
                return;
            }
            Auth.setCookie(ex, token);
            json(ex, 200, "{\"authenticated\":true}");
        } catch (Exception e) {
            error(ex, e);
        }
    }

    private static void logout(HttpExchange ex) throws IOException {
        Auth.logout(ex);
        json(ex, 200, "{\"authenticated\":false}");
    }

    private static boolean requireSession(HttpExchange ex) throws IOException {
        if (Auth.current(ex) == null) {
            json(ex, 401, "{\"error\":\"Authentication required.\"}");
            return false;
        }
        return true;
    }

    private static void dashboard(HttpExchange ex) throws IOException {
        if (!requireSession(ex)) return;
        try {
            List<String[]> free = service.query(
                "SELECT vt.type_name AS Type, COUNT(*) AS Free_Slots FROM slot s "
              + "JOIN vehicle_type vt ON s.type_id = vt.type_id WHERE s.is_occupied = 0 GROUP BY vt.type_name");
            List<String[]> payments = service.query(
                "SELECT IFNULL(SUM(amount),0) AS Total_Rs, COUNT(*) AS Payments FROM payment "
              + "WHERE DATE(pay_time) = CURDATE()");
            List<String[]> totals = service.query("SELECT COUNT(*) AS Total_Slots FROM slot");
            List<String[]> parked = service.query(
                "SELECT t.ticket_id AS Ticket, v.reg_no AS Reg_No, v.owner_name AS Owner, vt.type_name AS Type, "
              + "s.slot_number AS Slot, t.entry_time AS Entry_Time FROM ticket t "
              + "JOIN vehicle v ON t.vehicle_id = v.vehicle_id JOIN vehicle_type vt ON v.type_id = vt.type_id "
              + "JOIN slot s ON t.slot_id = s.slot_id WHERE t.status = 'ACTIVE' ORDER BY t.entry_time");
            int available = 0;
            for (int i = 1; i < free.size(); i++) available += Integer.parseInt(free.get(i)[1]);
            String revenue = payments.size() > 1 ? payments.get(1)[0] : "0";
            int totalSlots = totals.size() > 1 ? Integer.parseInt(totals.get(1)[0]) : available + parked.size() - 1;
            json(ex, 200, "{\"availableSlots\":" + available + ",\"totalSlots\":" + totalSlots
                + ",\"occupiedSlots\":" + Math.max(0, parked.size() - 1) + ",\"dailyRevenue\":"
                + number(revenue) + ",\"freeByType\":" + rows(free) + ",\"parkedVehicles\":" + rows(parked) + "}");
        } catch (Exception e) {
            error(ex, e);
        }
    }

    private static void slots(HttpExchange ex) throws IOException {
        try {
            List<String[]> rows = service.query(
                "SELECT s.slot_number AS slot, vt.type_name AS type, "
              + "CASE WHEN EXISTS (SELECT 1 FROM ev_charger c JOIN ev_session es ON es.charger_id = c.charger_id "
              + "WHERE c.slot_id = s.slot_id AND es.status = 'CHARGING') THEN 'CHARGING' "
              + "WHEN s.is_occupied = 1 THEN 'OCCUPIED' "
              + "WHEN EXISTS (SELECT 1 FROM reservation r WHERE r.slot_id = s.slot_id AND r.status = 'BOOKED' "
              + "AND r.start_time < NOW() + INTERVAL 60 MINUTE AND r.end_time > NOW()) THEN 'RESERVED' "
              + "ELSE 'FREE' END AS status, "
              + "IFNULL((SELECT v.reg_no FROM ticket t JOIN vehicle v ON t.vehicle_id = v.vehicle_id "
              + "WHERE t.slot_id = s.slot_id AND t.status = 'ACTIVE' LIMIT 1), '') AS reg "
              + "FROM slot s JOIN vehicle_type vt ON s.type_id = vt.type_id ORDER BY s.slot_id");
            json(ex, 200, rows(rows));
        } catch (Exception e) {
            error(ex, e);
        }
    }

    private static void park(HttpExchange ex) throws IOException {
        if (!requireSession(ex)) return;
        try {
            Map<String, String> f = parse(body(ex));
            String reg = cleanReg(f.get("reg"));
            String owner = f.getOrDefault("owner", "").trim();
            String phone = f.getOrDefault("phone", "").trim();
            int type = Integer.parseInt(f.getOrDefault("type", "1"));
            if (owner.isEmpty() || owner.length() > 50) throw new IllegalStateException("Name must contain 1-50 characters.");
            if (!phone.matches("[0-9+ -]{0,15}")) throw new IllegalStateException("Invalid phone number.");
            if (type < 1 || type > 3) throw new IllegalStateException("Invalid vehicle type.");
            Vehicle vehicle = type == 1 ? new Car(reg, owner, phone) : type == 2
                ? new Bike(reg, owner, phone) : new EVCar(reg, owner, phone);
            int ticket = service.parkVehicle(vehicle);
            json(ex, 201, "{\"ticketId\":" + ticket + "}");
        } catch (Exception e) {
            error(ex, e);
        }
    }

    private static void checkout(HttpExchange ex) throws IOException {
        if (!requireSession(ex)) return;
        try {
            String reg = cleanReg(parse(ex.getRequestURI().getRawQuery()).get("reg"));
            Bill bill = service.previewBill(reg);
            json(ex, 200, billJson(bill));
        } catch (Exception e) {
            error(ex, e);
        }
    }

    private static void exit(HttpExchange ex) throws IOException {
        if (!requireSession(ex)) return;
        try {
            Map<String, String> f = parse(body(ex));
            String mode = f.getOrDefault("mode", "Cash");
            if (!mode.equals("Cash") && !mode.equals("Card") && !mode.equals("UPI")) mode = "Cash";
            Bill bill = service.exitVehicle(cleanReg(f.get("reg")), mode, f.get("txn"));
            json(ex, 200, billJson(bill));
        } catch (Exception e) {
            error(ex, e);
        }
    }

    private static void startCharging(HttpExchange ex) throws IOException {
        if (!requireSession(ex)) return;
        try {
            ev.start(cleanReg(parse(body(ex)).get("reg")));
            json(ex, 200, "{\"message\":\"Charging started.\"}");
        } catch (Exception e) {
            error(ex, e);
        }
    }

    private static void stopCharging(HttpExchange ex) throws IOException {
        if (!requireSession(ex)) return;
        try {
            Map<String, String> f = parse(body(ex));
            String units = f.getOrDefault("units", "").trim();
            double[] result = ev.stop(cleanReg(f.get("reg")), units.isEmpty() ? null : Double.valueOf(units));
            json(ex, 200, "{\"units\":" + number(String.valueOf(result[0])) + ",\"amount\":"
                + number(String.valueOf(result[1])) + "}");
        } catch (Exception e) {
            error(ex, e);
        }
    }

    private static String billJson(Bill b) {
        return "{\"ticketId\":" + b.ticketId + ",\"registration\":\"" + quote(b.regNo) + "\",\"owner\":\""
            + quote(b.owner) + "\",\"vehicleType\":\"" + quote(b.typeName) + "\",\"slot\":\"" + quote(b.slotNumber)
            + "\",\"entry\":\"" + quote(format(b.entry)) + "\",\"minutes\":" + b.minutes + ",\"hours\":" + b.hours
            + ",\"rate\":" + number(String.format(US, "%.2f", b.rate)) + ",\"parking\":"
            + number(String.format(US, "%.2f", b.parking)) + ",\"evCharging\":"
            + number(String.format(US, "%.2f", b.ev)) + ",\"total\":"
            + number(String.format(US, "%.2f", b.total)) + "}";
    }

    private static String rows(List<String[]> data) {
        StringBuilder out = new StringBuilder("[");
        for (int i = 1; i < data.size(); i++) {
            if (i > 1) out.append(',');
            out.append('{');
            for (int j = 0; j < data.get(0).length; j++) {
                if (j > 0) out.append(',');
                String value = j < data.get(i).length ? data.get(i)[j] : "";
                out.append('"').append(quote(data.get(0)[j])).append("\":\"").append(quote(value)).append('"');
            }
            out.append('}');
        }
        return out.append(']').toString();
    }

    private static String format(LocalDateTime value) {
        return value == null ? "-" : value.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"));
    }

    private static String cleanReg(String value) {
        String reg = value == null ? "" : value.trim().toUpperCase();
        if (!reg.matches("[A-Z0-9 -]{4,15}")) throw new IllegalStateException("Registration number must contain 4-15 letters or numbers.");
        return reg;
    }

    private static String number(String value) {
        try { return String.format(US, "%.2f", Double.parseDouble(value)); }
        catch (NumberFormatException e) { return "0.00"; }
    }

    private static String quote(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", " ").replace("\n", " ");
    }

    private static Map<String, String> parse(String source) {
        Map<String, String> result = new HashMap<>();
        if (source == null || source.isEmpty()) return result;
        for (String pair : source.split("&")) {
            String[] parts = pair.split("=", 2);
            result.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
        }
        return result;
    }

    private static String body(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void error(HttpExchange ex, Exception error) throws IOException {
        int status = error instanceof SQLException ? 500 : 400;
        json(ex, status, "{\"error\":\"" + quote(error.getMessage() == null ? "Request failed." : error.getMessage()) + "\"}");
    }

    private static void json(HttpExchange ex, int status, String payload) throws IOException {
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, data.length);
        try (OutputStream out = ex.getResponseBody()) { out.write(data); }
    }
}
