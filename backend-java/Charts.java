import java.util.List;
import java.util.Locale;

// Simple SVG bar chart without a JavaScript library.
public class Charts {
    public static String bar(String title, List<String> labels, List<Double> values, String color) {
        int W = 640, H = 250, L = 46, R = 10, T = 32, B = 36;
        double max = 1;
        for (double v : values) max = Math.max(max, v);
        int n = Math.max(1, values.size());
        double slot = (double) (W - L - R) / n;
        double bw = Math.max(4, slot - 8);
        StringBuilder sb = new StringBuilder();
        sb.append("<svg viewBox='0 0 ").append(W).append(' ').append(H).append("' style='width:100%;height:auto'>");
        sb.append("<text x='").append(L).append("' y='18' font-size='14' font-weight='bold' fill='#1e40af'>")
          .append(Html.esc(title)).append("</text>");
        sb.append("<line x1='").append(L).append("' y1='").append(H - B).append("' x2='").append(W - R)
          .append("' y2='").append(H - B).append("' stroke='#94a3b8'/>");
        sb.append("<text x='").append(L - 4).append("' y='").append(T + 4)
          .append("' font-size='10' text-anchor='end' fill='#64748b'>").append(String.format(Locale.US, "%.0f", max)).append("</text>");
        sb.append("<text x='").append(L - 4).append("' y='").append(H - B)
          .append("' font-size='10' text-anchor='end' fill='#64748b'>0</text>");
        for (int i = 0; i < values.size(); i++) {
            double v = values.get(i);
            double h = v / max * (H - T - B);
            double x = L + i * slot + (slot - bw) / 2;
            double y = H - B - h;
            sb.append(String.format(Locale.US, "<rect x='%.1f' y='%.1f' width='%.1f' height='%.1f' fill='%s' rx='2'/>", x, y, bw, h, color));
            if (v > 0) sb.append(String.format(Locale.US, "<text x='%.1f' y='%.1f' font-size='10' text-anchor='middle' fill='#334155'>%.0f</text>",
                    x + bw / 2, y - 3, v));
            sb.append(String.format(Locale.US, "<text x='%.1f' y='%d' font-size='10' text-anchor='middle' fill='#64748b'>%s</text>",
                    x + bw / 2, H - B + 14, Html.esc(labels.get(i))));
        }
        return sb.append("</svg>").toString();
    }
}
