import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static java.util.Locale.US;

// Web interface: http://localhost:8080
public class WebServer {

    private static final ParkingService service = new ParkingService();
    private static final EvService ev = new EvService();
    private static final ReservationService resv = new ReservationService();

    interface Route { void run(HttpExchange ex, Auth.Session s) throws Exception; }

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(Config.PORT), 0);
        // level: 0 = public, 1 = staff, 2 = admin only
        server.createContext("/", route(1, WebServer::home));
        server.createContext("/login", route(0, WebServer::login));
        server.createContext("/logout", route(0, (ex, s) -> { Auth.logout(ex); redirect(ex, "/login"); }));
        server.createContext("/map", route(0, WebServer::map));
        server.createContext("/api/slots", route(0, WebServer::apiSlots));
        server.createContext("/verify", route(0, WebServer::verify));
        server.createContext("/reserve", route(0, WebServer::reserve));
        server.createContext("/reservation", route(0, WebServer::reservation));
        server.createContext("/park", route(1, WebServer::park));
        server.createContext("/ticket", route(1, WebServer::ticket));
        server.createContext("/checkout", route(1, WebServer::checkout));
        server.createContext("/exit", route(1, WebServer::exit));
        server.createContext("/bill", route(1, WebServer::bill));
        server.createContext("/history", route(1, WebServer::history));
        server.createContext("/checkin", route(1, WebServer::checkin));
        server.createContext("/ev", route(1, WebServer::evPage));
        server.createContext("/analytics", route(2, WebServer::analytics));
        server.start();
        System.out.println("Server started at " + Config.BASE_URL + "  (press Ctrl+C to stop)");
        System.out.println("Default login: admin / admin123   or   guard / guard123");
    }

    // ---------- routing + login check ----------
    private static HttpHandler route(int level, Route r) {
        return ex -> {
            try {
                Auth.Session s = Auth.current(ex);
                if (level >= 1 && s == null) {
                    redirect(ex, "/login?next=" + Html.enc(ex.getRequestURI().toString()));
                    return;
                }
                if (level >= 2 && !s.isAdmin()) {
                    send(ex, 403, Html.page("Access denied", s, "<div class='toast toast-error'>ERROR: This page is available to administrators only.</div>"));
                    return;
                }
                r.run(ex, s);
            } catch (Exception e) {
                e.printStackTrace();
                send(ex, 500, Html.page("Error", Auth.current(ex),
                    "<div class='toast toast-error'>ERROR: " + Html.esc(String.valueOf(e.getMessage())) + "</div><p><a href='/'>Home</a></p>"));
            }
        };
    }

    // ---------- LOGIN ----------
    private static void login(HttpExchange ex, Auth.Session s) throws Exception {
        Map<String, String> q = parse(ex.getRequestURI().getRawQuery());
        String next = q.getOrDefault("next", "/");
        String error = "";
        if ("POST".equals(ex.getRequestMethod())) {
            Map<String, String> f = parse(body(ex));
            next = f.getOrDefault("next", "/");
            String token = Auth.login(f.getOrDefault("username", "").trim(), f.getOrDefault("password", ""));
            if (token != null) {
                Auth.setCookie(ex, token);
                redirect(ex, (next.startsWith("/") && !next.startsWith("//")) ? next : "/");
                return;
            }
            error = "ERROR: Incorrect username or password.";
        }
        String b = "<div class='box' style='max-width:380px;margin:40px auto'><h2>Staff Login</h2>" + Html.msgBox(error)
            + "<form method='post' action='/login'>"
            + "<input type='hidden' name='next' value='" + Html.esc(next) + "'>"
            + "<input name='username' placeholder='Username' required autofocus>"
            + "<input type='password' name='password' placeholder='Password' required>"
            + "<button>Login</button></form>"
            + "<p class='subtle'>Need a parking reservation? <a href='/reserve'>Reserve a slot</a> or <a href='/map'>view the live map</a>.</p></div>";
        send(ex, 200, Html.page("Login", s, b));
    }

    // ---------- DASHBOARD ----------
    private static void home(HttpExchange ex, Auth.Session s) throws Exception {
        if (!ex.getRequestURI().getPath().equals("/")) {
            send(ex, 404, Html.page("404", s, "<div class='toast toast-error'>ERROR: Page not found.</div>"));
            return;
        }
        resv.expireOld();
        Map<String, String> q = parse(ex.getRequestURI().getRawQuery());
        StringBuilder h = new StringBuilder(Html.msgBox(q.getOrDefault("msg", "")));
        try {
            List<String[]> freeSlots = service.query(
                "SELECT vt.type_name AS Type, COUNT(*) AS Free_Slots FROM slot s "
              + "JOIN vehicle_type vt ON s.type_id = vt.type_id WHERE s.is_occupied = 0 GROUP BY vt.type_name");
            List<String[]> dailyPayments = service.query(
                "SELECT IFNULL(SUM(amount),0) AS Total_Rs, COUNT(*) AS Payments FROM payment "
              + "WHERE DATE(pay_time) = CURDATE()");
            List<String[]> parkedVehicles = service.query(
                "SELECT t.ticket_id AS Ticket, v.reg_no AS Reg_No, v.owner_name AS Owner, vt.type_name AS Type, "
              + "s.slot_number AS Slot, t.entry_time AS Entry_Time FROM ticket t "
              + "JOIN vehicle v ON t.vehicle_id = v.vehicle_id JOIN vehicle_type vt ON v.type_id = vt.type_id "
              + "JOIN slot s ON t.slot_id = s.slot_id WHERE t.status = 'ACTIVE' ORDER BY t.entry_time");
            String available = "0";
            for (int i = 1; i < freeSlots.size(); i++) available = String.valueOf(Integer.parseInt(available) + Integer.parseInt(freeSlots.get(i)[1]));
            String revenue = dailyPayments.size() > 1 ? "Rs. " + dailyPayments.get(1)[0] : "Rs. 0.00";
            h.append("<div class='page-heading'><div><div class='eyebrow'>Operations overview</div><h2>Parking dashboard</h2><p class='subtle'>Monitor capacity, entries, and payments in real time.</p></div></div>");
            h.append("<div class='metrics'>")
             .append(Html.metric("Available slots", available, "S", "blue"))
             .append(Html.metric("Parked vehicles", String.valueOf(Math.max(0, parkedVehicles.size() - 1)), "V", "green"))
             .append(Html.metric("Daily revenue", revenue, "R", "amber"))
             .append("</div>");
            h.append("<div class='row'>");
            h.append("<div class='box'><h3>Available slots by type</h3>").append(Html.table(freeSlots)).append("</div>");
            h.append("<div class='box'><h3>Today's revenue</h3>").append(Html.table(dailyPayments)).append("</div></div>");

            h.append("<div class='row'>");
            h.append("<div class='box'><h3>Vehicle entry</h3><form method='post' action='/park'>"
                + "<select name='type'><option value='1'>Car</option><option value='2'>Bike</option><option value='3'>EV Car</option></select>"
                + "<input name='reg' placeholder='Registration No (MH12AB1234)' required>"
                + "<input name='owner' placeholder='Owner name' required>"
                + "<input name='phone' placeholder='Phone'>"
                + "<button type='submit'>Park vehicle</button></form></div>");
            h.append("<div class='box'><h3>Vehicle exit</h3>"
                + "<form method='get' action='/checkout'><input name='reg' placeholder='Registration No' required>"
                + "<button type='submit'>Create bill &amp; exit</button></form>"
                + "<h3 style='margin-top:18px'>Reservation Check-in</h3>"
                + "<form method='post' action='/checkin'><input name='code' placeholder='Booking code (6 letters)' required maxlength='6'>"
                + "<button type='submit'>Check in</button></form>"
                + "<h3 style='margin-top:18px'>Vehicle History</h3>"
                + "<form method='get' action='/history'><input name='reg' placeholder='Registration No' required>"
                + "<button type='submit'>Search history</button></form></div></div>");

            h.append("<div class='box'><h3>Parked vehicles</h3>").append(Html.table(parkedVehicles)).append("</div>");

            h.append("<div class='box'><h3>Upcoming Reservations</h3>").append(Html.table(service.query(
                "SELECT r.confirm_code AS Code, r.reg_no AS Reg_No, r.owner_name AS Owner, vt.type_name AS Type, "
              + "s.slot_number AS Slot, r.start_time AS Start_Time, r.end_time AS End_Time FROM reservation r "
              + "JOIN vehicle_type vt ON r.type_id = vt.type_id JOIN slot s ON r.slot_id = s.slot_id "
              + "WHERE r.status = 'BOOKED' ORDER BY r.start_time LIMIT 15"))).append("</div>");

            h.append("<div class='box'><h3>Recent Payments</h3>").append(Html.table(service.query(
                "SELECT p.payment_id AS Payment, v.reg_no AS Reg_No, p.amount AS Amount_Rs, p.mode AS Mode, "
              + "p.pay_time AS Paid_At FROM payment p JOIN ticket t ON p.ticket_id = t.ticket_id "
              + "JOIN vehicle v ON t.vehicle_id = v.vehicle_id ORDER BY p.pay_time DESC LIMIT 10"))).append("</div>");
        } catch (SQLException e) {
            h.append(Html.msgBox("DB ERROR: " + e.getMessage()));
        }
        send(ex, 200, Html.page("Dashboard", s, h.toString()));
    }

    // ---------- ENTRY ----------
    private static void park(HttpExchange ex, Auth.Session s) throws Exception {
        if (!"POST".equals(ex.getRequestMethod())) { redirect(ex, "/"); return; }
        Map<String, String> f = parse(body(ex));
        try {
            String reg = cleanReg(f.get("reg"));
            String owner = f.getOrDefault("owner", "").trim();
            String phone = f.getOrDefault("phone", "").trim();
            if (owner.isEmpty() || owner.length() > 50) throw new IllegalStateException("Owner name 1-50 characters ka hona chahiye");
            if (!phone.matches("[0-9+ -]{0,15}")) throw new IllegalStateException("Invalid phone number");
            int type = Integer.parseInt(f.getOrDefault("type", "1"));
            Vehicle v = type == 1 ? new Car(reg, owner, phone) : type == 2 ? new Bike(reg, owner, phone) : new EVCar(reg, owner, phone);
            int ticketId = service.parkVehicle(v);
            redirect(ex, "/ticket?id=" + ticketId);
        } catch (SlotFullException e) {
            redirect(ex, "/?msg=" + Html.enc("SLOT FULL: " + e.getMessage()));
        } catch (IllegalStateException | NumberFormatException e) {
            redirect(ex, "/?msg=" + Html.enc("ERROR: " + e.getMessage()));
        } catch (SQLException e) {
            redirect(ex, "/?msg=" + Html.enc("DB ERROR: " + e.getMessage()));
        }
    }

    // ---------- QR TICKET ----------
    private static String verifyUrl(int ticketId) {
        return Config.BASE_URL + "/verify?t=" + ticketId + "." + Auth.sign("T" + ticketId);
    }

    private static void ticket(HttpExchange ex, Auth.Session s) throws Exception {
        int id = Integer.parseInt(parse(ex.getRequestURI().getRawQuery()).getOrDefault("id", "0"));
        Bill b = service.getBill(id);
        String body = "<div class='box pass'><h2>Parking Ticket #" + b.ticketId + "</h2>"
            + QrCode.encode(verifyUrl(b.ticketId)).toSvg(2, 200)
            + "<table><tr><th>Vehicle</th><td>" + Html.esc(b.regNo) + " (" + Html.esc(b.typeName) + ")</td></tr>"
            + "<tr><th>Owner</th><td>" + Html.esc(b.owner) + "</td></tr>"
            + "<tr><th>Slot</th><td><b>" + Html.esc(b.slotNumber) + "</b></td></tr>"
            + "<tr><th>Entry</th><td>" + fmt(b.entry) + "</td></tr>"
            + "<tr><th>Rate</th><td>Rs. " + String.format(US, "%.2f", b.rate) + " / hour</td></tr></table>"
            + "<p class='subtle'>Show this QR code at the exit.</p>"
            + "<p class='noprint'><button onclick='window.print()'>Print Ticket</button> <a class='btn btn2' href='/'>Dashboard</a></p></div>";
        send(ex, 200, Html.page("Ticket", s, body));
    }

    // Public QR verification page; only basic ticket status is shown.
    private static void verify(HttpExchange ex, Auth.Session s) throws Exception {
        String t = parse(ex.getRequestURI().getRawQuery()).getOrDefault("t", "");
        String out;
        int dot = t.indexOf('.');
        boolean valid = false;
        int id = 0;
        if (dot > 0) {
            try { id = Integer.parseInt(t.substring(0, dot)); valid = Auth.sign("T" + id).equals(t.substring(dot + 1)); }
            catch (NumberFormatException ignored) { }
        }
        if (!valid) {
            out = "<div class='toast toast-error'>ERROR: Invalid or corrupted QR ticket.</div>";
        } else {
            Bill b = service.getBill(id);
            String m = b.regNo.length() > 4 ? b.regNo.substring(0, 2) + "****" + b.regNo.substring(b.regNo.length() - 2) : "****";
            out = "<div class='toast toast-success'>VALID TICKET</div><table><tr><th>Ticket</th><td>#" + b.ticketId + "</td></tr>"
                + "<tr><th>Vehicle</th><td>" + Html.esc(m) + " (" + Html.esc(b.typeName) + ")</td></tr>"
                + "<tr><th>Slot</th><td>" + Html.esc(b.slotNumber) + "</td></tr>"
                + "<tr><th>Entry</th><td>" + fmt(b.entry) + "</td></tr>"
                + "<tr><th>Status</th><td>" + ("ACTIVE".equals(b.status) ? "Currently parked" : "Exited (" + fmt(b.exit) + ")") + "</td></tr></table>";
            if (s != null && "ACTIVE".equals(b.status))
                out += "<p><a class='btn' href='/checkout?reg=" + Html.enc(b.regNo) + "'>Checkout / Bill</a></p>";
        }
        send(ex, 200, Html.page("Verify Ticket", s, "<div class='box pass'><h2>Ticket Verification</h2>" + out + "</div>"));
    }

    // ---------- CHECKOUT (automatic bill + payment) ----------
    private static void checkout(HttpExchange ex, Auth.Session s) throws Exception {
        Map<String, String> q = parse(ex.getRequestURI().getRawQuery());
        String reg;
        Bill b;
        try {
            reg = cleanReg(q.get("reg"));
            if (ev.isCharging(reg)) {
                redirect(ex, "/ev?msg=" + Html.enc("ERROR: Stop charging for this vehicle before checkout."));
                return;
            }
            b = service.previewBill(reg);
        } catch (IllegalStateException e) {
            redirect(ex, "/?msg=" + Html.enc("ERROR: " + e.getMessage()));
            return;
        }
        String upiUri = "upi://pay?pa=" + Html.enc(Config.UPI_VPA) + "&pn=" + Html.enc(Config.UPI_NAME)
            + "&am=" + String.format(US, "%.2f", b.total) + "&cu=INR&tn=" + Html.enc("Parking Ticket " + b.ticketId);
        String body = "<div class='box pass'><h2>Bill: " + Html.esc(b.regNo) + "</h2>" + billTable(b)
            + "<form method='post' action='/exit'>"
            + "<input type='hidden' name='reg' value='" + Html.esc(b.regNo) + "'>"
            + "<input type='hidden' name='am' value='" + String.format(US, "%.2f", b.total) + "'>"
            + "<select name='mode' id='mode' onchange='t()'><option>Cash</option><option>Card</option><option>UPI</option></select>"
            + "<div id='upi' style='display:none'><p>Scan with a UPI app and pay <b>Rs. " + String.format(US, "%.2f", b.total) + "</b>:</p>"
            + QrCode.encode(upiUri).toSvg(2, 200)
            + "<p class='subtle'>Enter the transaction ID / UTR shown by your UPI app.</p>"
            + "<input name='txn' id='txn' placeholder='UPI Transaction ID (UTR)' maxlength='30'></div>"
            + "<button>Payment Received &amp; Exit</button> <a class='btn btn2' href='/'>Cancel</a></form></div>"
            + "<script>function t(){var u=document.getElementById('mode').value==='UPI';"
            + "document.getElementById('upi').style.display=u?'block':'none';document.getElementById('txn').required=u;}</script>";
        send(ex, 200, Html.page("Checkout", s, body));
    }

    private static void exit(HttpExchange ex, Auth.Session s) throws Exception {
        if (!"POST".equals(ex.getRequestMethod())) { redirect(ex, "/"); return; }
        Map<String, String> f = parse(body(ex));
        try {
            String reg = cleanReg(f.get("reg"));
            String mode = f.getOrDefault("mode", "Cash");
            if (!mode.equals("Cash") && !mode.equals("Card") && !mode.equals("UPI")) mode = "Cash";
            String txn = null;
            if (mode.equals("UPI")) {
                txn = f.getOrDefault("txn", "").trim();
                if (!txn.matches("[A-Za-z0-9]{6,30}")) throw new IllegalStateException("UPI Transaction ID 6-30 letters/numbers ki honi chahiye");
                double expected = Double.parseDouble(f.getOrDefault("am", "-1"));
                if (Math.abs(service.previewBill(reg).total - expected) > 0.009)
                    throw new IllegalStateException("The bill amount changed. Please reopen checkout.");
            }
            Bill b = service.exitVehicle(reg, mode, txn);
            redirect(ex, "/bill?id=" + b.ticketId);
        } catch (IllegalStateException | NumberFormatException e) {
            redirect(ex, "/?msg=" + Html.enc("ERROR: " + e.getMessage()));
        } catch (SQLException e) {
            redirect(ex, "/?msg=" + Html.enc("DB ERROR: " + e.getMessage()));
        }
    }

    private static void bill(HttpExchange ex, Auth.Session s) throws Exception {
        int id = Integer.parseInt(parse(ex.getRequestURI().getRawQuery()).getOrDefault("id", "0"));
        Bill b = service.getBill(id);
        String body = "<div class='box pass'><h2>Parking Bill #" + b.ticketId + "</h2>" + billTable(b)
            + QrCode.encode(verifyUrl(b.ticketId)).toSvg(2, 140)
            + "<p class='subtle'>Thank you for parking with us.</p>"
            + "<p class='noprint'><button onclick='window.print()'>Print Bill</button> <a class='btn btn2' href='/'>Dashboard</a></p></div>";
        send(ex, 200, Html.page("Bill", s, body));
    }

    private static String billTable(Bill b) {
        StringBuilder t = new StringBuilder("<table>");
        t.append("<tr><th>Vehicle</th><td>").append(Html.esc(b.regNo)).append(" (").append(Html.esc(b.typeName)).append(")</td></tr>");
        t.append("<tr><th>Owner</th><td>").append(Html.esc(b.owner)).append("</td></tr>");
        t.append("<tr><th>Slot</th><td>").append(Html.esc(b.slotNumber)).append("</td></tr>");
        t.append("<tr><th>Entry</th><td>").append(fmt(b.entry)).append("</td></tr>");
        t.append("<tr><th>Exit</th><td>").append(b.exit == null ? "abhi tak (provisional)" : fmt(b.exit)).append("</td></tr>");
        t.append("<tr><th>Time</th><td>").append(b.minutes).append(" min = ").append(b.hours).append(" hour(s), rounded up</td></tr>");
        t.append("<tr><th>Parking</th><td>").append(b.hours).append(" x Rs. ").append(String.format(US, "%.2f", b.rate))
         .append(" = Rs. ").append(String.format(US, "%.2f", b.parking)).append("</td></tr>");
        if (b.ev > 0) t.append("<tr><th>EV Charging</th><td>Rs. ").append(String.format(US, "%.2f", b.ev)).append("</td></tr>");
        t.append("<tr><th>TOTAL</th><td class='big'>Rs. ").append(String.format(US, "%.2f", b.total)).append("</td></tr>");
        if (b.paid) {
            t.append("<tr><th>Paid via</th><td>").append(Html.esc(b.mode));
            if (b.upiTxn != null) t.append(" (Txn: ").append(Html.esc(b.upiTxn)).append(")");
            t.append("</td></tr>");
        }
        return t.append("</table>").toString();
    }

    private static void history(HttpExchange ex, Auth.Session s) throws Exception {
        String reg = parse(ex.getRequestURI().getRawQuery()).getOrDefault("reg", "").trim().toUpperCase();
        StringBuilder h = new StringBuilder("<div class='box'><h3>History: " + Html.esc(reg) + "</h3>");
        h.append(Html.table(service.query(
            "SELECT t.ticket_id AS Ticket, t.entry_time AS Entry, t.exit_time AS Exit_Time, t.status AS Status, "
          + "IFNULL(p.amount,0) AS Amount_Rs FROM ticket t JOIN vehicle v ON t.vehicle_id = v.vehicle_id "
          + "LEFT JOIN payment p ON p.ticket_id = t.ticket_id WHERE v.reg_no = ? ORDER BY t.entry_time DESC", reg)));
        h.append("<p><a href='/'>&larr; Dashboard</a></p></div>");
        send(ex, 200, Html.page("History", s, h.toString()));
    }

    // ---------- LIVE SLOT MAP ----------
    private static final String SLOT_SQL =
        "SELECT s.slot_number AS slot, vt.type_name AS type, "
      + "CASE WHEN EXISTS (SELECT 1 FROM ev_charger c JOIN ev_session es ON es.charger_id = c.charger_id "
      + "                  WHERE c.slot_id = s.slot_id AND es.status = 'CHARGING') THEN 'CHARGING' "
      + "     WHEN s.is_occupied = 1 THEN 'OCCUPIED' "
      + "     WHEN EXISTS (SELECT 1 FROM reservation r WHERE r.slot_id = s.slot_id AND r.status = 'BOOKED' "
      + "                  AND r.start_time < NOW() + INTERVAL 60 MINUTE AND r.end_time > NOW()) THEN 'RESERVED' "
      + "     ELSE 'FREE' END AS status, "
      + "IFNULL((SELECT v.reg_no FROM ticket t JOIN vehicle v ON t.vehicle_id = v.vehicle_id "
      + "        WHERE t.slot_id = s.slot_id AND t.status = 'ACTIVE' LIMIT 1), '') AS reg "
      + "FROM slot s JOIN vehicle_type vt ON s.type_id = vt.type_id ORDER BY s.slot_id";

    private static void apiSlots(HttpExchange ex, Auth.Session s) throws Exception {
        List<String[]> rows = service.query(SLOT_SQL);
        StringBuilder j = new StringBuilder("[");
        for (int i = 1; i < rows.size(); i++) {
            String[] r = rows.get(i);
            if (i > 1) j.append(',');
            j.append("{\"slot\":").append(jsonStr(r[0])).append(",\"type\":").append(jsonStr(r[1]))
             .append(",\"status\":").append(jsonStr(r[2]))
             .append(",\"reg\":").append(jsonStr(s != null ? r[3] : "")).append('}'); // Registration is visible to staff only.
        }
        j.append(']');
        byte[] data = j.toString().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }

    private static void map(HttpExchange ex, Auth.Session s) throws Exception {
        String body = "<div class='box'><h2>Live Parking Map</h2>"
            + "<p class='legend'><span style='background:#dcfce7'>Free</span><span style='background:#fee2e2'>Occupied</span>"
            + "<span style='background:#ffedd5'>Reserved</span><span style='background:#dbeafe'>EV Charging</span></p>"
            + "<p id='summary'><b>Loading...</b></p><div id='grid' class='grid'></div>"
            + "<p class='subtle'>This map refreshes automatically every four seconds.</p></div>"
            + "<script>function el(t,c,x){var e=document.createElement(t);if(c)e.className=c;if(x!==undefined)e.textContent=x;return e;}"
            + "function draw(list){var g=document.getElementById('grid');g.textContent='';var n={FREE:0,OCCUPIED:0,RESERVED:0,CHARGING:0};"
            + "list.forEach(function(x){n[x.status]++;var d=el('div','slot '+x.status);d.appendChild(el('b','',x.slot));"
            + "d.appendChild(el('small','',x.type));d.appendChild(el('small','',x.status));if(x.reg)d.appendChild(el('small','',x.reg));g.appendChild(d);});"
            + "document.getElementById('summary').textContent='Free: '+n.FREE+'  |  Occupied: '+n.OCCUPIED+'  |  Reserved: '+n.RESERVED+'  |  Charging: '+n.CHARGING;}"
            + "function load(){fetch('/api/slots').then(function(r){return r.json();}).then(draw).catch(function(){});}"
            + "load();setInterval(load,4000);</script>";
        send(ex, 200, Html.page("Live Map", s, body));
    }

    // ---------- ONLINE RESERVATION (public) ----------
    private static void reserve(HttpExchange ex, Auth.Session s) throws Exception {
        if ("POST".equals(ex.getRequestMethod())) {
            Map<String, String> f = parse(body(ex));
            try {
                String reg = cleanReg(f.get("reg"));
                String owner = f.getOrDefault("owner", "").trim();
                String phone = f.getOrDefault("phone", "").trim();
                if (owner.isEmpty() || owner.length() > 50) throw new IllegalStateException("Name must contain 1-50 characters");
                if (!phone.matches("[0-9+ -]{6,15}")) throw new IllegalStateException("Sahi phone number daalo");
                int type = Integer.parseInt(f.getOrDefault("type", "1"));
                if (type < 1 || type > 3) throw new IllegalStateException("Invalid vehicle type");
                LocalDateTime start = LocalDateTime.parse(f.getOrDefault("start", ""));
                int hours = Integer.parseInt(f.getOrDefault("hours", "1"));
                String code = resv.create(type, reg, owner, phone, start, hours);
                redirect(ex, "/reservation?code=" + code);
            } catch (IllegalStateException | NumberFormatException | java.time.format.DateTimeParseException e) {
                redirect(ex, "/reserve?msg=" + Html.enc("ERROR: " + e.getMessage()));
            }
            return;
        }
        String msg = parse(ex.getRequestURI().getRawQuery()).getOrDefault("msg", "");
        String nowStr = LocalDateTime.now().plusMinutes(30).withSecond(0).withNano(0).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
        String body = "<div class='box' style='max-width:460px;margin:auto'><h2>Reserve a slot</h2>" + Html.msgBox(msg)
            + "<form method='post' action='/reserve'>"
            + "<select name='type'><option value='1'>Car</option><option value='2'>Bike</option><option value='3'>EV Car (charging slot)</option></select>"
            + "<input name='reg' placeholder='Registration No' required>"
            + "<input name='owner' placeholder='Owner name' required>"
            + "<input name='phone' placeholder='Phone number' required>"
            + "<label>Arrival time</label><input type='datetime-local' name='start' value='" + nowStr + "' min='" + nowStr + "' required>"
            + "<label>Duration</label><select name='hours'>" + hourOptions() + "</select>"
            + "<button>Reserve Slot</button></form>"
            + "<p class='subtle'>Show the six-letter confirmation code at the gate. Check-in opens 15 minutes before the start time.</p>"
            + "<p><a href='/map'>View the live map</a></p></div>";
        send(ex, 200, Html.page("Reserve", s, body));
    }

    private static String hourOptions() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 12; i++) sb.append("<option value='").append(i).append("'>").append(i).append(" hour(s)</option>");
        return sb.toString();
    }

    private static void reservation(HttpExchange ex, Auth.Session s) throws Exception {
        if (ex.getRequestURI().getPath().endsWith("/cancel")) {
            Map<String, String> f = parse(body(ex));
            String code = f.getOrDefault("code", "").trim().toUpperCase();
            try {
                resv.cancel(code);
                redirect(ex, "/reservation?code=" + Html.enc(code));
            } catch (IllegalStateException e) {
                redirect(ex, "/reserve?msg=" + Html.enc("ERROR: " + e.getMessage()));
            }
            return;
        }
        String code = parse(ex.getRequestURI().getRawQuery()).getOrDefault("code", "").trim().toUpperCase();
        List<String[]> rows = service.query(
            "SELECT r.confirm_code, r.reg_no, r.owner_name, vt.type_name, s.slot_number, r.start_time, r.end_time, r.status "
          + "FROM reservation r JOIN vehicle_type vt ON r.type_id = vt.type_id JOIN slot s ON r.slot_id = s.slot_id "
          + "WHERE r.confirm_code = ?", code);
        if (rows.size() < 2) {
            send(ex, 404, Html.page("Reservation", s, "<div class='box pass'><div class='toast toast-error'>ERROR: Confirmation code not found.</div><a href='/reserve'>Create a new reservation</a></div>"));
            return;
        }
        String[] r = rows.get(1);
        String qr = QrCode.encode(Config.BASE_URL + "/reservation?code=" + r[0]).toSvg(2, 180);
        String cancel = "BOOKED".equals(r[7])
            ? "<form method='post' action='/reservation/cancel' class='noprint'><input type='hidden' name='code' value='" + Html.esc(r[0])
              + "'><button class='btn2'>Cancel reservation</button></form>" : "";
        String body = "<div class='box pass'><h2>Reservation Confirmed</h2><p class='big'>" + Html.esc(r[0]) + "</p>" + qr
            + "<table><tr><th>Vehicle</th><td>" + Html.esc(r[1]) + " (" + Html.esc(r[3]) + ")</td></tr>"
            + "<tr><th>Owner</th><td>" + Html.esc(r[2]) + "</td></tr>"
            + "<tr><th>Slot</th><td>" + Html.esc(r[4]) + "</td></tr>"
            + "<tr><th>From</th><td>" + Html.esc(r[5]) + "</td></tr><tr><th>Until</th><td>" + Html.esc(r[6]) + "</td></tr>"
            + "<tr><th>Status</th><td><b>" + Html.esc(r[7]) + "</b></td></tr></table>" + cancel
            + "<p class='noprint'><button onclick='window.print()'>Print</button></p></div>";
        send(ex, 200, Html.page("Reservation", s, body));
    }

    private static void checkin(HttpExchange ex, Auth.Session s) throws Exception {
        if (!"POST".equals(ex.getRequestMethod())) { redirect(ex, "/"); return; }
        String code = parse(body(ex)).getOrDefault("code", "").trim().toUpperCase();
        try {
            redirect(ex, "/ticket?id=" + resv.checkIn(code));
        } catch (IllegalStateException e) {
            redirect(ex, "/?msg=" + Html.enc("ERROR: " + e.getMessage()));
        }
    }

    // ---------- EV CHARGING ----------
    private static void evPage(HttpExchange ex, Auth.Session s) throws Exception {
        String path = ex.getRequestURI().getPath();
        if ("POST".equals(ex.getRequestMethod())) {
            Map<String, String> f = parse(body(ex));
            String msg;
            try {
                if (path.equals("/ev/start")) {
                    ev.start(cleanReg(f.get("reg")));
                    msg = "Charging started.";
                } else if (path.equals("/ev/stop")) {
                    String u = f.getOrDefault("units", "").trim();
                    Double manual = null;
                    if (!u.isEmpty()) {
                        manual = Double.parseDouble(u);
                        if (manual < 0 || manual > 500) throw new IllegalStateException("Units must be between 0 and 500 kWh.");
                    }
                    double[] r = ev.stop(cleanReg(f.get("reg")), manual);
                    msg = "Charging stopped. Units: " + String.format(US, "%.2f", r[0]) + " kWh, Charge: Rs. " + String.format(US, "%.2f", r[1])
                        + " (will be added to the exit bill)";
                } else if (path.equals("/ev/config") && s.isAdmin()) {
                    ev.setRate(Double.parseDouble(f.getOrDefault("rate", "0")));
                    msg = "Charging rate updated.";
                } else {
                    msg = "ERROR: This action is not allowed.";
                }
            } catch (IllegalStateException | NumberFormatException e) {
                msg = "ERROR: " + e.getMessage();
            }
            redirect(ex, "/ev?msg=" + Html.enc(msg));
            return;
        }
        String msg = parse(ex.getRequestURI().getRawQuery()).getOrDefault("msg", "");
        StringBuilder h = new StringBuilder(Html.msgBox(msg));
        h.append("<div class='box'><h3>EV Chargers</h3>").append(Html.table(service.query(
            "SELECT c.charger_id AS Charger, s.slot_number AS Slot, c.power_kw AS kW, c.rate_per_kwh AS Rs_per_kWh, "
          + "c.status AS Status, IFNULL(v.reg_no,'-') AS Vehicle, IFNULL(es.start_time,'-') AS Charging_Since "
          + "FROM ev_charger c JOIN slot s ON s.slot_id = c.slot_id "
          + "LEFT JOIN ev_session es ON es.charger_id = c.charger_id AND es.status = 'CHARGING' "
          + "LEFT JOIN ticket t ON es.ticket_id = t.ticket_id LEFT JOIN vehicle v ON t.vehicle_id = v.vehicle_id "
          + "ORDER BY c.charger_id"))).append("</div>");
        h.append("<div class='row'><div class='box'><h3>Start Charging</h3><form method='post' action='/ev/start'>"
            + "<input name='reg' placeholder='Registration No (parked in an EV slot)' required><button type='submit'>Start</button></form></div>"
            + "<div class='box'><h3>Stop Charging</h3><form method='post' action='/ev/stop'>"
            + "<input name='reg' placeholder='Registration No' required>"
            + "<input name='units' type='number' step='0.01' min='0' placeholder='Meter units kWh (optional; defaults to power x time)'>"
            + "<button>Stop</button></form></div></div>");
        if (s.isAdmin()) {
            h.append("<div class='box'><h3>Admin: Charging rate (all chargers)</h3><form method='post' action='/ev/config'>"
                + "<input name='rate' type='number' step='0.01' min='0.01' placeholder='Rs per kWh' required><button>Update Rate</button></form></div>");
        }
        h.append("<div class='box'><h3>Recent Charging Sessions</h3>").append(Html.table(service.query(
            "SELECT es.session_id AS Session, v.reg_no AS Vehicle, es.charger_id AS Charger, es.start_time AS Start_Time, "
          + "IFNULL(es.end_time,'-') AS End_Time, es.units_kwh AS kWh, es.amount AS Amount_Rs, es.status AS Status "
          + "FROM ev_session es JOIN ticket t ON es.ticket_id = t.ticket_id JOIN vehicle v ON t.vehicle_id = v.vehicle_id "
          + "ORDER BY es.session_id DESC LIMIT 10"))).append("</div>");
        send(ex, 200, Html.page("EV Charging", s, h.toString()));
    }

    // ---------- REVENUE ANALYTICS (admin) ----------
    private static void analytics(HttpExchange ex, Auth.Session s) throws Exception {
        StringBuilder h = new StringBuilder("<h2>Revenue Analytics</h2>");
        String[] sum = service.query(
            "SELECT IFNULL(SUM(CASE WHEN DATE(pay_time) = CURDATE() THEN amount END),0), "
          + "IFNULL(SUM(CASE WHEN DATE(pay_time) >= CURDATE() - INTERVAL 6 DAY THEN amount END),0), "
          + "IFNULL(SUM(CASE WHEN YEAR(pay_time) = YEAR(CURDATE()) AND MONTH(pay_time) = MONTH(CURDATE()) THEN amount END),0), "
          + "IFNULL(SUM(amount),0), COUNT(*) FROM payment").get(1);
        String[] avg = service.query("SELECT IFNULL(ROUND(AVG(TIMESTAMPDIFF(MINUTE, entry_time, exit_time))),0) FROM ticket WHERE status = 'COMPLETED'").get(1);
        String[] occ = service.query("SELECT IFNULL(SUM(is_occupied),0), COUNT(*) FROM slot").get(1);
        String[] evs = service.query("SELECT IFNULL(SUM(units_kwh),0), IFNULL(SUM(amount),0) FROM ev_session WHERE status = 'COMPLETED'").get(1);
        h.append("<div class='cards'>")
         .append(card("Today", "Rs. " + sum[0])).append(card("Last 7 days", "Rs. " + sum[1]))
         .append(card("This month", "Rs. " + sum[2])).append(card("Total", "Rs. " + sum[3] + " (" + sum[4] + " payments)"))
         .append("</div><div class='cards'>")
         .append(card("Current occupancy", occ[0] + " / " + occ[1] + " slots"))
         .append(card("Avg parking time", avg[0] + " min"))
         .append(card("EV Charging", evs[0] + " kWh = Rs. " + evs[1]))
         .append("</div>");

        // Revenue for the last 14 days.
        Map<String, Double> byDay = new HashMap<>();
        List<String[]> d = service.query("SELECT DATE(pay_time), SUM(amount) FROM payment WHERE DATE(pay_time) >= CURDATE() - INTERVAL 13 DAY GROUP BY DATE(pay_time)");
        for (int i = 1; i < d.size(); i++) byDay.put(d.get(i)[0], Double.parseDouble(d.get(i)[1]));
        List<String> dl = new ArrayList<>();
        List<Double> dv = new ArrayList<>();
        for (int i = 13; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            dl.add(day.format(DateTimeFormatter.ofPattern("dd/MM")));
            dv.add(byDay.getOrDefault(day.toString(), 0.0));
        }
        h.append("<div class='box'>").append(Charts.bar("Daily revenue (Rs) - last 14 days", dl, dv, "#2563eb")).append("</div>");

        // peak hours
        double[] hrs = new double[24];
        List<String[]> ph = service.query("SELECT HOUR(entry_time), COUNT(*) FROM ticket GROUP BY HOUR(entry_time)");
        for (int i = 1; i < ph.size(); i++) hrs[Integer.parseInt(ph.get(i)[0])] = Double.parseDouble(ph.get(i)[1]);
        List<String> hl = new ArrayList<>();
        List<Double> hv = new ArrayList<>();
        for (int i = 0; i < 24; i++) { hl.add(String.valueOf(i)); hv.add(hrs[i]); }
        h.append("<div class='box'>").append(Charts.bar("Peak hours - entries per hour of day", hl, hv, "#f97316")).append("</div>");

        h.append("<div class='row'><div class='box'>").append(chartFrom("Revenue by vehicle type (Rs)",
            "SELECT vt.type_name, SUM(p.amount) FROM payment p JOIN ticket t ON p.ticket_id = t.ticket_id "
          + "JOIN vehicle v ON t.vehicle_id = v.vehicle_id JOIN vehicle_type vt ON v.type_id = vt.type_id GROUP BY vt.type_name", "#16a34a"))
         .append("</div><div class='box'>").append(chartFrom("Revenue by payment mode (Rs)",
            "SELECT p.mode, SUM(p.amount) FROM payment p GROUP BY p.mode", "#9333ea")).append("</div></div>");
        h.append("<p class='subtle'>Note: revenue includes parking and EV charging.</p>");
        send(ex, 200, Html.page("Analytics", s, h.toString()));
    }

    private static String card(String title, String value) {
        return "<div class='card'><small>" + Html.esc(title) + "</small><b>" + Html.esc(value) + "</b></div>";
    }

    private static String chartFrom(String title, String sql, String color) throws SQLException {
        List<String[]> rows = service.query(sql);
        List<String> l = new ArrayList<>();
        List<Double> v = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) { l.add(rows.get(i)[0]); v.add(Double.parseDouble(rows.get(i)[1])); }
        if (l.isEmpty()) return "<h3>" + Html.esc(title) + "</h3><p class='empty-state'>No data available yet.</p>";
        return Charts.bar(title, l, v, color);
    }

    // ---------- HELPERS ----------
    private static String cleanReg(String r) {
        String reg = (r == null ? "" : r).trim().toUpperCase();
        if (!reg.matches("[A-Z0-9 -]{4,15}")) throw new IllegalStateException("Registration number must contain 4-15 letters or numbers");
        return reg;
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? "-" : t.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"));
    }

    private static String jsonStr(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : (s == null ? "" : s).toCharArray()) {
            if (c == '"' || c == '\\') sb.append('\\').append(c);
            else if (c < 32) sb.append(' ');
            else sb.append(c);
        }
        return sb.append('"').toString();
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

    private static void send(HttpExchange ex, int status, String html) throws IOException {
        byte[] data = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        ex.sendResponseHeaders(status, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }

    private static void redirect(HttpExchange ex, String loc) throws IOException {
        ex.getResponseHeaders().set("Location", loc);
        ex.sendResponseHeaders(303, -1);
        ex.close();
    }
}
