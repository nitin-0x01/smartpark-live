import java.nio.charset.StandardCharsets;
import java.util.List;

// Shared HTML presentation helpers.
public class Html {

    public static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    public static String enc(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public static String table(List<String[]> rows) {
        if (rows.size() <= 1) return "<p class='empty-state'>No records found.</p>";
        StringBuilder t = new StringBuilder("<div class='table-wrap'><table><thead><tr>");
        for (String c : rows.get(0)) t.append("<th>").append(esc(formatHeader(c))).append("</th>");
        t.append("</tr></thead><tbody>");
        for (int i = 1; i < rows.size(); i++) {
            t.append("<tr>");
            for (int j = 0; j < rows.get(i).length; j++) {
                String value = rows.get(i)[j];
                String header = j < rows.get(0).length ? rows.get(0)[j] : "";
                t.append("<td data-label='").append(esc(formatHeader(header))).append("'>")
                    .append(cellValue(header, value)).append("</td>");
            }
            t.append("</tr>");
        }
        return t.append("</tbody></table></div>").toString();
    }

    private static String formatHeader(String value) {
        if (value == null || value.isEmpty()) return "";
        return value.replace('_', ' ');
    }

    private static String cellValue(String header, String value) {
        String safe = esc(value);
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if ("CAR".equals(normalized) || "BIKE".equals(normalized) || "EV CAR".equals(normalized)
                || "ELECTRIC CAR".equals(normalized)) {
            return "<span class='badge badge-type'>" + safe + "</span>";
        }
        if ("ACTIVE".equals(normalized) || "COMPLETED".equals(normalized) || "BOOKED".equals(normalized)
                || "FREE".equals(normalized) || "AVAILABLE".equals(normalized)) {
            return "<span class='badge badge-success'>" + safe + "</span>";
        }
        if ("OCCUPIED".equals(normalized) || "CANCELLED".equals(normalized) || "EXPIRED".equals(normalized)
                || "CHARGING".equals(normalized)) {
            return "<span class='badge badge-warning'>" + safe + "</span>";
        }
        return safe;
    }

    public static String msgBox(String msg) {
        if (msg == null || msg.isEmpty()) return "";
        boolean bad = msg.startsWith("ERROR") || msg.startsWith("SLOT FULL") || msg.startsWith("DB ERROR");
        return "<div class='toast toast-" + (bad ? "error" : "success") + "' role='status'>"
            + "<span class='toast-icon'>" + (bad ? "!" : "✓") + "</span><span>" + esc(msg) + "</span></div>";
    }

    public static String metric(String label, String value, String icon, String tone) {
        return "<div class='metric metric-" + esc(tone) + "'><div class='metric-icon'>" + icon
            + "</div><div><span>" + esc(label) + "</span><strong>" + esc(value) + "</strong></div></div>";
    }

    public static String page(String title, Auth.Session s, String body) {
        StringBuilder h = new StringBuilder();
        h.append("<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'>")
         .append("<meta name='viewport' content='width=device-width, initial-scale=1'>")
         .append("<meta name='theme-color' content='#111827'><title>").append(esc(title)).append(" | Parkwise</title>")
         .append("<link rel='preconnect' href='https://fonts.googleapis.com'>")
         .append("<link rel='preconnect' href='https://fonts.gstatic.com' crossorigin>")
         .append("<link href='https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=Space+Grotesk:wght@500;600;700&display=swap' rel='stylesheet'>")
         .append("<style>")
         .append(":root{--ink:#172033;--muted:#6b7280;--line:#e5e7eb;--surface:#fff;--canvas:#f5f7fb;--brand:#2563eb;--brand-dark:#1d4ed8;--green:#059669;--amber:#d97706;--red:#dc2626;--shadow:0 12px 32px rgba(15,23,42,.07)}")
         .append("*{box-sizing:border-box}body{font-family:'DM Sans',sans-serif;background:var(--canvas);color:var(--ink);margin:0;line-height:1.5}")
         .append("h1,h2,h3{font-family:'Space Grotesk',sans-serif;letter-spacing:-.02em}h2{font-size:25px;margin:0 0 6px}h3{font-size:17px;margin:0 0 16px}a{color:var(--brand);text-decoration:none}a:hover{color:var(--brand-dark)}")
         .append(".topbar{background:#111827;color:#fff;min-height:72px;padding:0 5vw;display:flex;align-items:center;gap:25px}.brand{display:flex;align-items:center;gap:10px;color:#fff;font-family:'Space Grotesk';font-size:20px;font-weight:700;margin-right:auto}.brand-mark{display:grid;place-items:center;width:34px;height:34px;background:linear-gradient(135deg,#60a5fa,#2563eb);border-radius:10px}.nav{display:flex;align-items:center;gap:18px}.nav a{color:#cbd5e1;font-size:13px;font-weight:600}.nav a:hover{color:#fff}.user-pill{border:1px solid #374151;border-radius:999px;padding:7px 12px;color:#e5e7eb;font-size:12px}")
         .append(".menu-toggle{display:none;background:transparent;padding:5px;font-size:22px}.wrap{max-width:1280px;margin:0 auto;padding:38px 5vw 60px}.page-heading{display:flex;justify-content:space-between;align-items:flex-end;gap:20px;margin-bottom:27px}.eyebrow{color:var(--brand);font-size:12px;font-weight:700;text-transform:uppercase;letter-spacing:.12em}.subtle,.empty-state{color:var(--muted);font-size:14px}.row{display:flex;gap:20px;flex-wrap:wrap}.row>.box{flex:1;min-width:280px}.box{background:var(--surface);border:1px solid rgba(226,232,240,.8);border-radius:18px;padding:24px;margin-bottom:20px;box-shadow:var(--shadow)}")
         .append(".metrics{display:grid;grid-template-columns:repeat(3,1fr);gap:16px;margin-bottom:24px}.metric{background:var(--surface);border:1px solid var(--line);border-radius:18px;padding:20px;display:flex;align-items:center;gap:14px;box-shadow:var(--shadow)}.metric-icon{width:44px;height:44px;border-radius:13px;display:grid;place-items:center;font-size:20px;background:#dbeafe;color:#1d4ed8}.metric-green .metric-icon{background:#d1fae5;color:#047857}.metric-amber .metric-icon{background:#fef3c7;color:#b45309}.metric span{display:block;color:var(--muted);font-size:12px;font-weight:600}.metric strong{display:block;font-family:'Space Grotesk';font-size:25px;margin-top:3px}")
         .append("form{display:grid;gap:11px}label{font-size:13px;color:#4b5563;font-weight:600}input,select{display:block;width:100%;padding:12px 13px;background:#fff;border:1px solid #d1d5db;border-radius:10px;color:var(--ink);font:inherit;font-size:14px;outline:none;transition:border .2s,box-shadow .2s}input:focus,select:focus{border-color:var(--brand);box-shadow:0 0 0 3px rgba(37,99,235,.12)}input:invalid:not(:placeholder-shown){border-color:#fca5a5}.form-actions{display:flex;gap:10px;align-items:center;flex-wrap:wrap;margin-top:4px}button,.btn{background:var(--brand);color:#fff;border:0;padding:11px 16px;border-radius:10px;cursor:pointer;font:600 14px 'DM Sans';text-decoration:none;display:inline-flex;align-items:center;justify-content:center;gap:7px;transition:transform .2s,background .2s,box-shadow .2s}button:hover,.btn:hover{background:var(--brand-dark);color:#fff;transform:translateY(-1px);box-shadow:0 6px 14px rgba(37,99,235,.2)}.btn2{background:#eef2f7;color:#374151}.btn2:hover{background:#e5e7eb;color:#111827}.btn-danger{background:#fee2e2;color:#b91c1c}.btn-danger:hover{background:#fecaca;color:#991b1b}")
         .append(".table-wrap{overflow-x:auto}table{width:100%;border-collapse:collapse;font-size:13px}th,td{padding:14px 12px;border-bottom:1px solid #eef0f4;text-align:left;white-space:nowrap}th{color:#6b7280;text-transform:uppercase;letter-spacing:.06em;font-size:10px;font-weight:700}tbody tr:hover{background:#f8fafc}.badge{display:inline-flex;align-items:center;border-radius:999px;padding:4px 9px;font-size:11px;font-weight:700}.badge-type{background:#eff6ff;color:#1d4ed8}.badge-success{background:#d1fae5;color:#047857}.badge-warning{background:#fef3c7;color:#b45309}")
         .append(".toast{display:flex;align-items:center;gap:10px;padding:13px 16px;border-radius:12px;margin-bottom:20px;font-weight:600;font-size:14px;animation:slideIn .35s ease}.toast-success{background:#ecfdf5;color:#047857;border:1px solid #a7f3d0}.toast-error{background:#fef2f2;color:#b91c1c;border:1px solid #fecaca}.toast-icon{display:grid;place-items:center;width:22px;height:22px;border-radius:50%;background:currentColor;color:#fff;font-size:12px}.empty-state{padding:18px 0}.big{font:700 28px 'Space Grotesk';color:var(--brand)}.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(135px,1fr));gap:14px}.slot{border-radius:14px;padding:16px;text-align:center;border:1px solid transparent;transition:transform .2s}.slot:hover{transform:translateY(-3px)}.slot b{display:block;font:700 23px 'Space Grotesk'}.slot small{display:block;color:#475569;font-size:11px;margin-top:3px}.slot.FREE{background:#ecfdf5;border-color:#a7f3d0}.slot.OCCUPIED{background:#fef2f2;border-color:#fecaca}.slot.RESERVED{background:#fff7ed;border-color:#fed7aa}.slot.CHARGING{background:#eff6ff;border-color:#bfdbfe}.legend{display:flex;gap:8px;flex-wrap:wrap}.legend span{display:inline-flex;padding:5px 10px;border-radius:999px;font-size:12px;font-weight:600}.pass{max-width:520px;margin:20px auto;text-align:center}.pass table{text-align:left;margin-top:18px}.chart{max-width:100%;height:auto}")
         .append("@keyframes slideIn{from{opacity:0;transform:translateY(-8px)}to{opacity:1;transform:translateY(0)}}@media(max-width:760px){.topbar{padding:12px 5vw;flex-wrap:wrap}.menu-toggle{display:block}.nav{display:none;width:100%;flex-direction:column;align-items:flex-start;padding:10px 0}.nav.open{display:flex}.user-pill{margin-left:auto}.wrap{padding-top:25px}.page-heading{display:block}.metrics{grid-template-columns:1fr}.box{padding:18px;border-radius:14px}th,td{padding:11px 8px}.table-wrap table{min-width:600px}.row>.box{min-width:100%}}@media print{.topbar,.noprint{display:none!important}body{background:#fff}.box{box-shadow:none;border:0}}")
         .append("</style></head><body><header class='topbar'><a class='brand' href='/'><span class='brand-mark'>P</span>Parkwise</a><button class='menu-toggle' aria-label='Toggle navigation' onclick=\"document.querySelector('.nav').classList.toggle('open')\">☰</button><nav class='nav'>")
         .append("<a href='/map'>Live Map</a><a href='/reserve'>Reserve</a>");
        if (s != null) {
            h.append("<a href='/'>Dashboard</a><a href='/ev'>EV Charging</a>");
            if (s.isAdmin()) h.append("<a href='/analytics'>Analytics</a>");
            h.append("<a class='user-pill' href='/logout'>").append(esc(s.username)).append(" · Sign out</a>");
        } else {
            h.append("<a href='/login'>Staff Login</a>");
        }
        h.append("</nav></header><main class='wrap'>").append(body).append("</main>")
         .append("<script>document.querySelectorAll('form').forEach(function(form){form.addEventListener('submit',function(){var b=form.querySelector('button[type=submit],button:not([type])');if(b){b.disabled=true;b.dataset.label=b.textContent;b.textContent='Please wait...';}});});</script>")
         .append("</body></html>");
        return h.toString();
    }
}
