import com.sun.net.httpserver.HttpExchange;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

// Login, session cookies, and QR token signing.
public class Auth {

    public static class Session {
        public final String username, role;
        long expires;
        Session(String username, String role) { this.username = username; this.role = role; }
        public boolean isAdmin() { return "ADMIN".equals(role); }
    }

    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final SecureRandom RNG = new SecureRandom();
    private static final long TTL_MS = 2 * 60 * 60 * 1000L; // Two hours.

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    static String sha256(String s) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    // HMAC signature prevents forged QR tickets.
    public static String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Config.SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return hex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8))).substring(0, 10);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    // Returns a session token for valid credentials, otherwise null.
    public static String login(String user, String pass) throws SQLException {
        String sql = "SELECT salt, password_hash, role FROM admin_user WHERE username = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, user);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                byte[] calc = sha256(rs.getString("salt") + pass).getBytes(StandardCharsets.UTF_8);
                byte[] saved = rs.getString("password_hash").toLowerCase().getBytes(StandardCharsets.UTF_8);
                if (MessageDigest.isEqual(calc, saved)) {
                    byte[] raw = new byte[16];
                    RNG.nextBytes(raw);
                    String token = hex(raw);
                    Session s = new Session(user, rs.getString("role"));
                    s.expires = System.currentTimeMillis() + TTL_MS;
                    SESSIONS.put(token, s);
                    return token;
                }
            }
        }
        try { Thread.sleep(600); } catch (InterruptedException ignored) { } // brute-force slow
        return null;
    }

    private static String cookieToken(HttpExchange ex) {
        String c = ex.getRequestHeaders().getFirst("Cookie");
        if (c == null) return null;
        for (String part : c.split(";")) {
            part = part.trim();
            if (part.startsWith("SID=")) return part.substring(4);
        }
        return null;
    }

    public static Session current(HttpExchange ex) {
        String t = cookieToken(ex);
        if (t == null) return null;
        Session s = SESSIONS.get(t);
        if (s == null) return null;
        if (s.expires < System.currentTimeMillis()) { SESSIONS.remove(t); return null; }
        s.expires = System.currentTimeMillis() + TTL_MS;
        return s;
    }

    public static void setCookie(HttpExchange ex, String token) {
        ex.getResponseHeaders().add("Set-Cookie", "SID=" + token + "; Path=/; HttpOnly; SameSite=Strict");
    }

    public static void logout(HttpExchange ex) {
        String t = cookieToken(ex);
        if (t != null) SESSIONS.remove(t);
        ex.getResponseHeaders().add("Set-Cookie", "SID=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict");
    }
}
