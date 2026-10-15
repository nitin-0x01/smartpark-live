import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebServer {

    private static final ParkingService service = new ParkingService();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", WebServer::customerView);
        server.createContext("/admin", WebServer::adminView);
        server.createContext("/park", WebServer::park);
        server.createContext("/exit", WebServer::exit);
        server.createContext("/book", WebServer::book);
        server.createContext("/api/state", WebServer::apiState);
        server.createContext("/api/book", WebServer::apiBook);
        server.createContext("/api/park", WebServer::apiPark);
        server.createContext("/api/scan", WebServer::apiScan);
        server.createContext("/api/exit", WebServer::apiExit);
        server.start();
        System.out.println("========================================================");
        System.out.println(" SmartPark Customer Portal -> http://localhost:8080");
        System.out.println(" SmartPark Admin Console   -> http://localhost:8080/admin");
        System.out.println("========================================================");
    }

    private static void apiState(HttpExchange ex) throws IOException {
        if (!allowCors(ex)) return;
        try {
            String slots = rowsAsJson(service.query(
                "SELECT s.slot_id, s.slot_number, s.type_id, vt.type_name, s.is_occupied, "
              + "s.status, COALESCE(s.location_hint, '') AS location_hint FROM slot s "
              + "JOIN vehicle_type vt ON s.type_id = vt.type_id ORDER BY s.slot_id"));
            String types = rowsAsJson(service.query(
                "SELECT type_id, type_name, rate_per_hour FROM vehicle_type ORDER BY type_id"));
            String tickets = rowsAsJson(service.query(
                "SELECT t.ticket_id, t.vehicle_id, v.reg_no, v.owner_name, v.phone, t.slot_id, "
              + "s.slot_number, COALESCE(s.location_hint, '') AS location_hint, v.type_id, "
              + "vt.type_name, t.entry_time, t.exit_time, t.status FROM ticket t "
              + "JOIN vehicle v ON t.vehicle_id = v.vehicle_id JOIN slot s ON t.slot_id = s.slot_id "
              + "JOIN vehicle_type vt ON v.type_id = vt.type_id ORDER BY t.ticket_id DESC"));
            String payments = rowsAsJson(service.query(
                "SELECT p.payment_id, p.ticket_id, v.reg_no, p.amount, p.pay_time, p.mode, "
              + "'SUCCESS' AS status, p.transaction_ref FROM payment p "
              + "JOIN ticket t ON p.ticket_id = t.ticket_id JOIN vehicle v ON t.vehicle_id = v.vehicle_id "
              + "ORDER BY p.payment_id DESC"));
            sendJson(ex, "{\"vehicleTypes\":" + types + ",\"slots\":" + slots
                + ",\"vehicles\":[],\"tickets\":" + tickets + ",\"payments\":" + payments
                + ",\"reservations\":[],\"evSessions\":[],\"users\":[],"
                + "\"nextTicketId\":0,\"nextPaymentId\":0,\"nextReservationId\":0,"
                + "\"nextSessionId\":0,\"nextVehicleId\":0}");
        } catch (SQLException e) {
            sendJsonError(ex, e.getMessage());
        }
    }

    private static void apiBook(HttpExchange ex) throws IOException {
        apiParkLike(ex);
    }

    private static void apiPark(HttpExchange ex) throws IOException {
        apiParkLike(ex);
    }

    private static void apiParkLike(HttpExchange ex) throws IOException {
        if (!allowCors(ex)) return;
        if (!"POST".equals(ex.getRequestMethod())) {
            sendJsonError(ex, "POST required");
            return;
        }
        Map<String, String> f = parse(body(ex));
        try {
            String reg = f.getOrDefault("reg", "").trim().toUpperCase();
            String owner = f.getOrDefault("owner", "Driver").trim();
            String phone = f.getOrDefault("phone", "N/A").trim();
            int typeId = Integer.parseInt(f.getOrDefault("typeId", f.getOrDefault("type", "1")));
            Integer slotId = f.containsKey("slotId") && !f.get("slotId").isEmpty()
                ? Integer.valueOf(f.get("slotId")) : null;
            Vehicle v = typeId == 1 ? new Car(reg, owner, phone)
                : typeId == 2 ? new Bike(reg, owner, phone) : new EVCar(reg, owner, phone);
            int ticketId = service.parkVehicle(v, slotId);
            List<String[]> result = service.query(
                "SELECT t.ticket_id, t.vehicle_id, v.reg_no, v.owner_name, v.phone, t.slot_id, "
              + "s.slot_number, COALESCE(s.location_hint, '') AS location_hint, v.type_id, "
              + "vt.type_name, t.entry_time, t.status FROM ticket t "
              + "JOIN vehicle v ON t.vehicle_id=v.vehicle_id JOIN slot s ON t.slot_id=s.slot_id "
              + "JOIN vehicle_type vt ON v.type_id=vt.type_id WHERE t.ticket_id = ?", String.valueOf(ticketId));
            sendJson(ex, "{\"success\":true,\"ticket\":" + ticketAsJson(result.get(1))
                + ",\"message\":\"Vehicle " + jsonEscape(reg) + " parked successfully.\"}");
        } catch (Exception e) {
            sendJsonError(ex, e.getMessage());
        }
    }

    private static void apiScan(HttpExchange ex) throws IOException {
        if (!allowCors(ex)) return;
        Map<String, String> f = parse(body(ex));
        String token = f.getOrDefault("qr", "").trim();
        try {
            List<String[]> rows = service.query(
                "SELECT t.ticket_id, t.vehicle_id, v.reg_no, v.owner_name, v.phone, t.slot_id, "
              + "s.slot_number, COALESCE(s.location_hint, '') AS location_hint, v.type_id, "
              + "vt.type_name, t.entry_time, t.status FROM ticket t "
              + "JOIN vehicle v ON t.vehicle_id=v.vehicle_id JOIN slot s ON t.slot_id=s.slot_id "
              + "JOIN vehicle_type vt ON v.type_id=vt.type_id WHERE t.status='ACTIVE' "
              + "AND (v.reg_no=? OR CAST(t.ticket_id AS CHAR)=? OR ? LIKE CONCAT('%TKT|', t.ticket_id, '%'))",
                token, token, token);
            if (rows.size() <= 1) {
                sendJson(ex, "{\"action\":\"INVALID\",\"success\":false,\"message\":\"Invalid or expired ticket.\"}");
                return;
            }
            sendJson(ex, "{\"action\":\"CHECK_OUT\",\"success\":true,\"ticket\":"
                + ticketAsJson(rows.get(1)) + ",\"message\":\"Ticket verified. Complete payment to exit.\"}");
        } catch (SQLException e) {
            sendJsonError(ex, e.getMessage());
        }
    }

    private static void apiExit(HttpExchange ex) throws IOException {
        if (!allowCors(ex)) return;
        Map<String, String> f = parse(body(ex));
        try {
            String reg = f.getOrDefault("reg", "").trim().toUpperCase();
            double amount = service.exitVehicle(reg, f.getOrDefault("mode", "UPI"));
            sendJson(ex, "{\"success\":true,\"amount\":" + amount
                + ",\"message\":\"Checkout complete for " + jsonEscape(reg) + ".\"}");
        } catch (Exception e) {
            sendJsonError(ex, e.getMessage());
        }
    }

    // =========================================================================
    // 1. CUSTOMER PORTAL (Simple, Live Slots, Wayfinding & Privacy Map)
    // =========================================================================
    private static void customerView(HttpExchange ex) throws IOException {
        Map<String, String> q = parse(ex.getRequestURI().getRawQuery());
        String msg = q.getOrDefault("msg", "");
        StringBuilder h = new StringBuilder(head("SmartPark - Driver Portal"));

        if (!msg.isEmpty()) {
            boolean bad = msg.startsWith("ERROR") || msg.startsWith("SLOT FULL") || msg.startsWith("DB ERROR");
            h.append("<div class='msg ").append(bad ? "err" : "ok").append("'>").append(esc(msg)).append("</div>");
        }

        try {
            // Live Stats (Car Free, Bike Free, EV Free)
            List<String[]> freeSummary = service.query(
                "SELECT vt.type_name, COUNT(*) AS free_bays FROM slot s "
              + "JOIN vehicle_type vt ON s.type_id = vt.type_id WHERE s.is_occupied = 0 GROUP BY vt.type_name"
            );

            h.append("<div class='banner'>")
             .append("<h2>Reserve Parking Slot Online</h2>")
             .append("<p>Real-time availability from MySQL • Instant digital pass • Automatic boom barrier entry</p>")
             .append("<div class='kpi-row'>");
            for (int i = 1; i < freeSummary.size(); i++) {
                h.append("<div class='kpi-card'><span>").append(esc(freeSummary.get(i)[0])).append(" Free</span><strong>")
                 .append(esc(freeSummary.get(i)[1])).append(" Bays</strong></div>");
            }
            h.append("</div></div>");

            // 3-Step Simple Booking Form
            h.append("<div class='card'>")
             .append("<h3>Step 1 &amp; 2: Select Vehicle Type &amp; Details</h3>")
             .append("<form method='post' action='/book'>")
             .append("<div class='form-grid'>")
             .append("<div><label>Vehicle Type</label><select name='type' id='vtype' onchange='filterSlots()'>")
             .append("<option value='1'>Car (Rs 40/hr)</option>")
             .append("<option value='2' selected>Bike (Rs 20/hr)</option>")
             .append("<option value='3'>EV Car (Rs 50/hr)</option></select></div>")
             .append("<div><label>Vehicle Reg No</label><input name='reg' placeholder='e.g. MH12AB1234' required style='text-transform:uppercase'></div>")
             .append("<div><label>Driver / Owner Name</label><input name='owner' placeholder='Your Name' required></div>")
             .append("<div><label>Mobile Number</label><input name='phone' placeholder='10-digit Phone' required></div>")
             .append("</div>");

            // Step 3: Choose Available Slot (With Wayfinding Directions from database!)
            h.append("<h3 style='margin-top:22px'>Step 3: Choose Your Bay (With Exact Location Directions)</h3>")
             .append("<label>Select Free Bay:</label><select name='slot_id' id='slotSelect' required>")
             .append("<option value=''>-- Choose an Available Slot --</option>");

            List<String[]> openSlots = service.query(
                "SELECT s.slot_id, s.slot_number, s.type_id, vt.type_name, s.location_hint "
              + "FROM slot s JOIN vehicle_type vt ON s.type_id = vt.type_id WHERE s.is_occupied = 0 ORDER BY s.type_id, s.slot_id"
            );

            for (int i = 1; i < openSlots.size(); i++) {
                String sid = openSlots.get(i)[0];
                String snum = openSlots.get(i)[1];
                String typeId = openSlots.get(i)[2];
                String stype = openSlots.get(i)[3];
                String hint = openSlots.get(i)[4];

                h.append("<option value='").append(sid).append("' data-type='").append(typeId).append("'>")
                 .append("Slot ").append(snum).append(" (").append(stype).append(") -> Directions: ").append(esc(hint))
                 .append("</option>");
            }
            h.append("</select>")
             .append("<p class='hint-text'>Har slot ke sath direction likhi hai taaki parking me aate hi seedha sahi bay mil jaye.</p>")
             .append("<button type='submit' class='btn-primary' style='margin-top:12px;width:100%;font-size:15px'>Confirm Reservation &amp; Get Pass</button></form></div>");

            // Live 15-Slot Floor Map (STRICT PRIVACY: kisi ka naam/number nahi dikhega!)
            h.append("<div class='card'>")
             .append("<h3>Live Parking Floor Map (All 15 Bays)</h3>")
             .append("<p style='font-size:12px;color:#64748b;margin-bottom:15px;'>Green = Available (Khali) • Red = Occupied (Parked). (Privacy Mode: Customer ko personal details nahi dikhengi)</p>")
             .append("<div class='grid-slots'>");

            List<String[]> allSlots = service.query(
                "SELECT s.slot_number, s.is_occupied, vt.type_name, s.location_hint FROM slot s "
              + "JOIN vehicle_type vt ON s.type_id = vt.type_id ORDER BY s.type_id, s.slot_id"
            );

            for (int i = 1; i < allSlots.size(); i++) {
                String snum = allSlots.get(i)[0];
                boolean isOcc = "1".equals(allSlots.get(i)[1]);
                String stype = allSlots.get(i)[2];
                String hint = allSlots.get(i)[3];

                String colorClass = isOcc ? "slot-occ" : "slot-avail";
                String stText = isOcc ? "OCCUPIED" : "AVAILABLE";

                h.append("<div class='slot-box ").append(colorClass).append("'>")
                 .append("<div class='slot-num'>").append(snum).append("</div>")
                 .append("<div class='slot-status'>").append(stText).append("</div>")
                 .append("<div style='font-size:10px;font-weight:bold;margin:2px 0;'>").append(stype).append("</div>")
                 .append("<div class='slot-dir'>").append(esc(hint)).append("</div>")
                 .append("</div>");
            }
            h.append("</div></div>");

        } catch (SQLException e) {
            h.append("<div class='msg err'>DB ERROR: ").append(esc(e.getMessage())).append("</div>");
        }

        // JavaScript to filter dropdown by vehicle type
        h.append("<script>")
         .append("function filterSlots(){")
         .append("  var vt = document.getElementById('vtype').value;")
         .append("  var sel = document.getElementById('slotSelect');")
         .append("  var opts = sel.getElementsByTagName('option');")
         .append("  sel.value = '';")
         .append("  for(var i=1; i<opts.length; i++){")
         .append("    var dtype = opts[i].getAttribute('data-type');")
         .append("    if(dtype === vt){ opts[i].style.display = 'block'; } else { opts[i].style.display = 'none'; }")
         .append("  }")
         .append("};")
         .append("window.onload = filterSlots;")
         .append("</script>");

        send(ex, h.append(foot(false)).toString());
    }

    // =========================================================================
    // 2. ADMIN PORTAL (Full Details, QR Barrier Scanner & Manual Entry/Exit)
    // =========================================================================
    private static void adminView(HttpExchange ex) throws IOException {
        Map<String, String> q = parse(ex.getRequestURI().getRawQuery());
        String msg = q.getOrDefault("msg", "");
        StringBuilder h = new StringBuilder(head("Admin Control Tower - SmartPark"));

        if (!msg.isEmpty()) {
            boolean bad = msg.startsWith("ERROR") || msg.startsWith("SLOT FULL") || msg.startsWith("DB ERROR");
            h.append("<div class='msg ").append(bad ? "err" : "ok").append("'>").append(esc(msg)).append("</div>");
        }

        try {
            h.append("<div class='admin-row'>")
             .append("<div class='card' style='flex:1'>")
             .append("<h3>Gate Barrier QR Scanner / Exit Checkout</h3>")
             .append("<form method='post' action='/exit'>")
             .append("<label>Vehicle Registration Number</label>")
             .append("<input name='reg' placeholder='Scan QR or Type Reg No (e.g. MH12AB1234)' required style='text-transform:uppercase'>")
             .append("<label style='margin-top:8px'>Payment Mode</label>")
             .append("<select name='mode'><option>UPI</option><option>Cash</option><option>Card</option></select>")
             .append("<button class='btn-primary' style='margin-top:10px;width:100%'>Process Checkout &amp; Open Barrier</button></form>")
             .append("</div>");

            h.append("<div class='card' style='flex:1'>")
             .append("<h3>Manual Gate Entry (Walk-In Drivers)</h3>")
             .append("<form method='post' action='/park'>")
             .append("<label>Vehicle Type</label>")
             .append("<select name='type'><option value='1'>Car (Rs 40)</option><option value='2'>Bike (Rs 20)</option><option value='3'>EV (Rs 50)</option></select>")
             .append("<label style='margin-top:6px'>Vehicle Reg No</label>")
             .append("<input name='reg' placeholder='e.g. BR12AB1234' required style='text-transform:uppercase'>")
             .append("<label style='margin-top:6px'>Owner Name</label>")
             .append("<input name='owner' placeholder='Owner Name' required>")
             .append("<label style='margin-top:6px'>Phone</label>")
             .append("<input name='phone' placeholder='Phone Number'>")
             .append("<button class='btn-success' style='margin-top:10px;width:100%'>Issue Ticket &amp; Park</button></form>")
             .append("</div></div>");

            // Full Details Table for Admin!
            h.append("<div class='card'>")
             .append("<h3>Currently Parked Vehicles (Admin Audit View)</h3>")
             .append(table(service.query(
                "SELECT t.ticket_id AS Ticket, v.reg_no AS Reg_No, v.owner_name AS Owner_Name, v.phone AS Phone, "
              + "vt.type_name AS Vehicle_Type, s.slot_number AS Slot, t.entry_time AS Entry_Time "
              + "FROM ticket t JOIN vehicle v ON t.vehicle_id = v.vehicle_id "
              + "JOIN vehicle_type vt ON v.type_id = vt.type_id "
              + "JOIN slot s ON t.slot_id = s.slot_id WHERE t.status = 'ACTIVE' ORDER BY t.entry_time DESC"
             )))
             .append("</div>");

            h.append("<div class='card'>")
             .append("<h3>Recent Financial Payment Transactions</h3>")
             .append(table(service.query(
                "SELECT p.payment_id AS Payment_ID, v.reg_no AS Reg_No, p.amount AS Amount_Rs, p.mode AS Mode, p.pay_time AS Paid_At "
              + "FROM payment p JOIN ticket t ON p.ticket_id = t.ticket_id "
              + "JOIN vehicle v ON t.vehicle_id = v.vehicle_id ORDER BY p.pay_time DESC LIMIT 10"
             )))
             .append("</div>");

        } catch (SQLException e) {
            h.append("<div class='msg err'>DB ERROR: ").append(esc(e.getMessage())).append("</div>");
        }

        send(ex, h.append(foot(true)).toString());
    }

    // =========================================================================
    // 3. ACTION HANDLERS
    // =========================================================================
    private static void book(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { redirect(ex, "/", ""); return; }
        Map<String, String> f = parse(body(ex));
        String msg;
        try {
            String reg = f.getOrDefault("reg", "").trim().toUpperCase();
            String owner = f.getOrDefault("owner", "").trim();
            String phone = f.getOrDefault("phone", "").trim();
            String type = f.getOrDefault("type", "1");
            int slotId = Integer.parseInt(f.getOrDefault("slot_id", "0"));

            if (reg.isEmpty() || slotId == 0) {
                throw new IllegalStateException("Registration No aur Slot choose karna zaroori hai!");
            }

            Vehicle v;
            if ("1".equals(type)) v = new Car(reg, owner, phone);
            else if ("2".equals(type)) v = new Bike(reg, owner, phone);
            else v = new EVCar(reg, owner, phone);

            int ticketId = service.parkVehicle(v);
            msg = "Booking Successful! Vehicle " + reg + " ke liye slot book ho gaya. Ticket ID: " + ticketId;
        } catch (Exception e) {
            msg = "ERROR: " + e.getMessage();
        }
        redirect(ex, "/", msg);
    }

    private static void park(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { redirect(ex, "/admin", ""); return; }
        Map<String, String> f = parse(body(ex));
        String msg;
        try {
            String reg = f.getOrDefault("reg", "").trim().toUpperCase();
            String owner = f.getOrDefault("owner", "").trim();
            String phone = f.getOrDefault("phone", "").trim();
            String type = f.getOrDefault("type", "1");

            Vehicle v;
            if ("1".equals(type)) v = new Car(reg, owner, phone);
            else if ("2".equals(type)) v = new Bike(reg, owner, phone);
            else v = new EVCar(reg, owner, phone);

            int ticketId = service.parkVehicle(v);
            msg = "Vehicle Parked! Ticket ID: " + ticketId;
        } catch (Exception e) {
            msg = "ERROR: " + e.getMessage();
        }
        redirect(ex, "/admin", msg);
    }

    private static void exit(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) { redirect(ex, "/admin", ""); return; }
        Map<String, String> f = parse(body(ex));
        String msg;
        try {
            String reg = f.getOrDefault("reg", "").trim().toUpperCase();
            double amount = service.exitVehicle(reg, f.getOrDefault("mode", "UPI"));
            msg = "Exit ho gaya! Vehicle " + reg + " ka bill: Rs. " + amount + " (" + f.getOrDefault("mode", "UPI") + "). Slot released.";
        } catch (Exception e) {
            msg = "ERROR: " + e.getMessage();
        }
        redirect(ex, "/admin", msg);
    }

    // =========================================================================
    // 4. UI STYLING & HELPERS
    // =========================================================================
    private static String head(String title) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1'>"
             + "<title>" + title + "</title><style>"
             + "*{box-sizing:border-box}body{font-family:'Segoe UI',sans-serif;background:#f8fafc;margin:0;color:#0f172a}"
             + "header{background:#0f172a;color:#fff;padding:16px 24px;display:flex;justify-content:space-between;align-items:center;border-bottom:3px solid #2563eb}"
             + ".logo{font-size:20px;font-weight:900;letter-spacing:-0.5px;color:#fff}.logo span{color:#38bdf8}"
             + "nav a{color:#94a3b8;text-decoration:none;font-size:13px;font-weight:bold;margin-left:14px;padding:6px 12px;border-radius:8px}"
             + "nav a:hover{background:#1e293b;color:#fff}"
             + ".container{max-width:1150px;margin:24px auto;padding:0 16px}"
             + ".banner{background:linear-gradient(135deg,#1e3a8a,#0f172a);color:#fff;padding:24px;border-radius:16px;margin-bottom:20px;box-shadow:0 4px 15px rgba(0,0,0,0.1)}"
             + ".banner h2{margin:0 0 6px;font-size:24px;font-weight:800}.banner p{margin:0;font-size:13px;color:#93c5fd}"
             + ".kpi-row{display:flex;gap:12px;margin-top:16px;flex-wrap:wrap}"
             + ".kpi-card{background:rgba(255,255,255,0.12);padding:10px 16px;border-radius:10px;font-size:12px}"
             + ".kpi-card strong{display:block;font-size:18px;color:#4ade80;margin-top:2px}"
             + ".card{background:#fff;border-radius:16px;padding:22px;margin-bottom:20px;border:1px solid #e2e8f0;box-shadow:0 1px 3px rgba(0,0,0,0.05)}"
             + ".card h3{margin:0 0 12px;color:#1e3a8a;font-size:16px;font-weight:800}"
             + ".form-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:12px}"
             + "label{display:block;font-size:11px;font-weight:700;color:#475569;margin-bottom:4px;text-transform:uppercase}"
             + "input,select{width:100%;padding:10px 12px;border:1px solid #cbd5e1;border-radius:8px;font-size:13px;background:#f8fafc}"
             + "input:focus,select:focus{border-color:#2563eb;outline:none;background:#fff}"
             + ".btn-primary{background:#2563eb;color:#fff;border:0;padding:12px 20px;border-radius:10px;cursor:pointer;font-weight:700;font-size:13px}"
             + ".btn-success{background:#16a34a;color:#fff;border:0;padding:12px 20px;border-radius:10px;cursor:pointer;font-weight:700;font-size:13px}"
             + ".btn-primary:hover{background:#1d4ed8}.btn-success:hover{background:#15803d}"
             + ".hint-text{font-size:11px;color:#64748b;margin:8px 0}"
             + ".grid-slots{display:grid;grid-template-columns:repeat(auto-fill,minmax(180px,1fr));gap:12px}"
             + ".slot-box{padding:12px;border-radius:12px;border:2px solid;text-align:center}"
             + ".slot-num{font-size:18px;font-weight:900;font-family:monospace}"
             + ".slot-status{font-size:10px;font-weight:800;padding:2px 6px;border-radius:4px;display:inline-block;margin:6px 0}"
             + ".slot-dir{font-size:10px;color:#475569;line-height:1.3;text-align:left;border-top:1px dashed rgba(0,0,0,0.1);padding-top:6px;margin-top:6px}"
             + ".slot-avail{background:#f0fdf4;border-color:#86efac;color:#166534}.slot-avail .slot-status{background:#dcfce7;color:#166534}"
             + ".slot-occ{background:#fef2f2;border-color:#fca5a5;color:#991b1b}.slot-occ .slot-status{background:#fee2e2;color:#991b1b}"
             + ".slot-res{background:#fffbeb;border-color:#fde68a;color:#92400e}.slot-res .slot-status{background:#fef3c7;color:#92400e}"
             + ".slot-ev{background:#ecfeff;border-color:#a5f3fc;color:#155e75}.slot-ev .slot-status{background:#cffafe;color:#155e75}"
             + "table{width:100%;border-collapse:collapse;font-size:13px}th,td{padding:10px;border-bottom:1px solid #e2e8f0;text-align:left}th{background:#f1f5f9;color:#475569}"
             + ".msg{padding:14px;border-radius:10px;margin-bottom:20px;font-weight:bold;font-size:13px}"
             + ".ok{background:#dcfce7;color:#15803d;border:1px solid #86efac}.err{background:#fee2e2;color:#b91c1c;border:1px solid #fca5a5}"
             + ".admin-row{display:flex;gap:16px;flex-wrap:wrap}"
             + "</style></head><body>"
             + "<header><div class='logo'>SmartPark <span>Live</span></div>"
             + "<nav><a href='/'>Customer Booking</a><a href='/admin'>Admin Console</a></nav></header>"
             + "<div class='container'>";
    }

    private static String foot(boolean isAdmin) {
        return "</div><footer style='text-align:center;padding:24px;font-size:12px;color:#94a3b8;border-top:1px solid #e2e8f0;'>"
             + "SmartPark Live Facility • " + (isAdmin ? "Admin Control" : "Customer Portal") + "</footer></body></html>";
    }

    private static String table(List<String[]> rows) {
        if (rows.size() <= 1) return "<p style='color:#94a3b8;font-size:13px;'>Abhi koi record nahi mila.</p>";
        StringBuilder t = new StringBuilder("<table><tr>");
        for (String c : rows.get(0)) t.append("<th>").append(esc(c)).append("</th>");
        t.append("</tr>");
        for (int i = 1; i < rows.size(); i++) {
            t.append("<tr>");
            for (String c : rows.get(i)) t.append("<td>").append(esc(c)).append("</td>");
            t.append("</tr>");
        }
        return t.append("</table>").toString();
    }

    private static Map<String, String> parse(String s) {
        Map<String, String> m = new HashMap<>();
        if (s == null || s.isEmpty()) return m;
        for (String p : s.split("&")) {
            String[] kv = p.split("=", 2);
            m.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                  kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return m;
    }

    private static String body(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;");
    }

    private static boolean allowCors(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "http://localhost:3000");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return false;
        }
        return true;
    }

    private static void sendJson(HttpExchange ex, String json) throws IOException {
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }

    private static void sendJsonError(HttpExchange ex, String message) throws IOException {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "http://localhost:3000");
        byte[] data = ("{\"success\":false,\"message\":\"" + jsonEscape(message) + "\"}")
            .getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(400, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }

    private static String rowsAsJson(List<String[]> rows) {
        if (rows.size() <= 1) return "[]";
        StringBuilder json = new StringBuilder("[");
        String[] headers = rows.get(0);
        for (int r = 1; r < rows.size(); r++) {
            if (r > 1) json.append(',');
            json.append('{');
            for (int c = 0; c < headers.length; c++) {
                if (c > 0) json.append(',');
                json.append('"').append(jsonEscape(toCamel(headers[c]))).append("\":");
                json.append('"').append(jsonEscape(rows.get(r)[c])).append('"');
            }
            json.append('}');
        }
        return json.append(']').toString();
    }

    private static String ticketAsJson(String[] row) {
        String[] keys = {"ticket_id", "vehicle_id", "reg_no", "owner_name", "phone", "slot_id",
            "slot_number", "location_hint", "type_id", "type_name", "entry_time", "status"};
        StringBuilder json = new StringBuilder("{");
        for (int i = 0; i < keys.length && i < row.length; i++) {
            if (i > 0) json.append(',');
            json.append('"').append(toCamel(keys[i])).append("\":\"")
                .append(jsonEscape(row[i])).append('"');
        }
        return json.append('}').toString();
    }

    private static String toCamel(String value) {
        StringBuilder result = new StringBuilder();
        boolean upper = false;
        for (char ch : value.toCharArray()) {
            if (ch == '_') { upper = true; continue; }
            result.append(upper ? Character.toUpperCase(ch) : ch);
            upper = false;
        }
        return result.toString();
    }

    private static String jsonEscape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\r", "\\r").replace("\n", "\\n");
    }

    private static void send(HttpExchange ex, String html) throws IOException {
        byte[] data = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }

    private static void redirect(HttpExchange ex, String path, String msg) throws IOException {
        String loc = msg.isEmpty() ? path : path + "?msg=" + URLEncoder.encode(msg, StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Location", loc);
        ex.sendResponseHeaders(303, -1);
        ex.close();
    }
}
