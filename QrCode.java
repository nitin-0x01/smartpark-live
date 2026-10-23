import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// Small pure-Java QR code generator. Versions 1-10, error correction level M.
// Supports about 210 bytes, which is sufficient for ticket and UPI links.
public final class QrCode {

    private static final int MAX_VER = 10;
    private static final int[] ECC_PER_BLOCK = {0, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26};
    private static final int[] NUM_BLOCKS    = {0,  1,  1,  1,  2,  2,  4,  4,  4,  5,  5};

    private final int version, size;
    private final boolean[][] modules;
    private final boolean[][] isFunction;

    public static QrCode encode(String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        for (int ver = 1; ver <= MAX_VER; ver++) {
            int dataCodewords = numRawDataModules(ver) / 8 - ECC_PER_BLOCK[ver] * NUM_BLOCKS[ver];
            int ccBits = ver <= 9 ? 8 : 16;
            if (4 + ccBits + 8 * data.length <= dataCodewords * 8) {
                List<Boolean> bb = new ArrayList<>();
                appendBits(bb, 0x4, 4);                 // byte mode
                appendBits(bb, data.length, ccBits);
                for (byte b : data) appendBits(bb, b & 0xFF, 8);
                int capBits = dataCodewords * 8;
                appendBits(bb, 0, Math.min(4, capBits - bb.size()));
                appendBits(bb, 0, (8 - bb.size() % 8) % 8);
                for (int pad = 0xEC; bb.size() < capBits; pad ^= 0xEC ^ 0x11) appendBits(bb, pad, 8);
                byte[] cw = new byte[bb.size() / 8];
                for (int i = 0; i < bb.size(); i++) if (bb.get(i)) cw[i >>> 3] |= 1 << (7 - (i & 7));
                return new QrCode(ver, cw);
            }
        }
        throw new IllegalArgumentException("Text is too long for a QR code");
    }

    private QrCode(int ver, byte[] dataCodewords) {
        version = ver;
        size = ver * 4 + 17;
        modules = new boolean[size][size];
        isFunction = new boolean[size][size];
        drawFunctionPatterns();
        drawCodewords(addEccAndInterleave(dataCodewords));
        int best = 0, bestPenalty = Integer.MAX_VALUE;
        for (int m = 0; m < 8; m++) {
            applyMask(m);
            drawFormatBits(m);
            int p = penalty();
            if (p < bestPenalty) { best = m; bestPenalty = p; }
            applyMask(m); // undo
        }
        applyMask(best);
        drawFormatBits(best);
    }

    public int size() { return size; }
    public boolean get(int x, int y) { return modules[y][x]; }

    public String toSvg(int border, int px) {
        int dim = size + border * 2;
        StringBuilder sb = new StringBuilder();
        sb.append("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 ").append(dim).append(' ').append(dim)
          .append("' width='").append(px).append("' height='").append(px).append("' shape-rendering='crispEdges'>");
        sb.append("<rect width='100%' height='100%' fill='#fff'/><path fill='#000' d='");
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++)
                if (modules[y][x]) sb.append('M').append(x + border).append(',').append(y + border).append("h1v1h-1z");
        return sb.append("'/></svg>").toString();
    }

    // ---------- function patterns ----------
    private void drawFunctionPatterns() {
        for (int i = 0; i < size; i++) {
            setFunction(6, i, i % 2 == 0);
            setFunction(i, 6, i % 2 == 0);
        }
        drawFinder(3, 3);
        drawFinder(size - 4, 3);
        drawFinder(3, size - 4);
        int[] pos = alignPositions();
        int n = pos.length;
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                if (!((i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0)))
                    drawAlignment(pos[i], pos[j]);
        drawFormatBits(0);
        drawVersion();
    }

    private void drawFormatBits(int mask) {
        int data = mask; // ECC level M = 00
        int rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        int bits = ((data << 10) | rem) ^ 0x5412;
        for (int i = 0; i <= 5; i++) setFunction(8, i, bit(bits, i));
        setFunction(8, 7, bit(bits, 6));
        setFunction(8, 8, bit(bits, 7));
        setFunction(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) setFunction(14 - i, 8, bit(bits, i));
        for (int i = 0; i < 8; i++) setFunction(size - 1 - i, 8, bit(bits, i));
        for (int i = 8; i < 15; i++) setFunction(8, size - 15 + i, bit(bits, i));
        setFunction(8, size - 8, true);
    }

    private void drawVersion() {
        if (version < 7) return;
        int rem = version;
        for (int i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
        int bits = version << 12 | rem;
        for (int i = 0; i < 18; i++) {
            boolean b = bit(bits, i);
            int a = size - 11 + i % 3, c = i / 3;
            setFunction(a, c, b);
            setFunction(c, a, b);
        }
    }

    private void drawFinder(int x, int y) {
        for (int dy = -4; dy <= 4; dy++)
            for (int dx = -4; dx <= 4; dx++) {
                int dist = Math.max(Math.abs(dx), Math.abs(dy));
                int xx = x + dx, yy = y + dy;
                if (0 <= xx && xx < size && 0 <= yy && yy < size) setFunction(xx, yy, dist != 2 && dist != 4);
            }
    }

    private void drawAlignment(int x, int y) {
        for (int dy = -2; dy <= 2; dy++)
            for (int dx = -2; dx <= 2; dx++)
                setFunction(x + dx, y + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
    }

    private void setFunction(int x, int y, boolean dark) {
        modules[y][x] = dark;
        isFunction[y][x] = true;
    }

    private int[] alignPositions() {
        if (version == 1) return new int[0];
        int numAlign = version / 7 + 2;
        int step = (version * 4 + numAlign * 2 + 1) / (numAlign * 2 - 2) * 2;
        int[] result = new int[numAlign];
        result[0] = 6;
        for (int i = result.length - 1, pos = size - 7; i >= 1; i--, pos -= step) result[i] = pos;
        return result;
    }

    // ---------- data + error correction ----------
    private byte[] addEccAndInterleave(byte[] data) {
        int numBlocks = NUM_BLOCKS[version];
        int blockEccLen = ECC_PER_BLOCK[version];
        int rawCodewords = numRawDataModules(version) / 8;
        int numShort = numBlocks - rawCodewords % numBlocks;
        int shortBlockLen = rawCodewords / numBlocks;
        byte[][] blocks = new byte[numBlocks][];
        byte[] div = rsDivisor(blockEccLen);
        for (int i = 0, k = 0; i < numBlocks; i++) {
            byte[] dat = Arrays.copyOfRange(data, k, k + shortBlockLen - blockEccLen + (i < numShort ? 0 : 1));
            k += dat.length;
            byte[] block = Arrays.copyOf(dat, shortBlockLen + 1);
            byte[] ecc = rsRemainder(dat, div);
            System.arraycopy(ecc, 0, block, block.length - blockEccLen, ecc.length);
            blocks[i] = block;
        }
        byte[] result = new byte[rawCodewords];
        for (int i = 0, k = 0; i < blocks[0].length; i++)
            for (int j = 0; j < blocks.length; j++)
                if (i != shortBlockLen - blockEccLen || j >= numShort) result[k++] = blocks[j][i];
        return result;
    }

    private void drawCodewords(byte[] data) {
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            for (int vert = 0; vert < size; vert++)
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (!isFunction[y][x] && i < data.length * 8) {
                        modules[y][x] = bit(data[i >>> 3], 7 - (i & 7));
                        i++;
                    }
                }
        }
    }

    private void applyMask(int mask) {
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                boolean invert;
                switch (mask) {
                    case 0: invert = (x + y) % 2 == 0; break;
                    case 1: invert = y % 2 == 0; break;
                    case 2: invert = x % 3 == 0; break;
                    case 3: invert = (x + y) % 3 == 0; break;
                    case 4: invert = (x / 3 + y / 2) % 2 == 0; break;
                    case 5: invert = x * y % 2 + x * y % 3 == 0; break;
                    case 6: invert = (x * y % 2 + x * y % 3) % 2 == 0; break;
                    default: invert = ((x + y) % 2 + x * y % 3) % 2 == 0; break;
                }
                modules[y][x] ^= invert & !isFunction[y][x];
            }
    }

    // simple penalty: lambi same-colour runs aur 2x2 blocks
    private int penalty() {
        int result = 0;
        for (int a = 0; a < size; a++) {
            int runR = 1, runC = 1;
            for (int b = 1; b < size; b++) {
                if (modules[a][b] == modules[a][b - 1]) { runR++; if (runR == 5) result += 3; else if (runR > 5) result++; } else runR = 1;
                if (modules[b][a] == modules[b - 1][a]) { runC++; if (runC == 5) result += 3; else if (runC > 5) result++; } else runC = 1;
            }
        }
        for (int y = 0; y < size - 1; y++)
            for (int x = 0; x < size - 1; x++) {
                boolean c = modules[y][x];
                if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) result += 3;
            }
        return result;
    }

    // ---------- helpers ----------
    private static int numRawDataModules(int ver) {
        int result = (16 * ver + 128) * ver + 64;
        if (ver >= 2) {
            int numAlign = ver / 7 + 2;
            result -= (25 * numAlign - 10) * numAlign - 55;
            if (ver >= 7) result -= 36;
        }
        return result;
    }

    private static byte[] rsDivisor(int degree) {
        byte[] result = new byte[degree];
        result[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < result.length; j++) {
                result[j] = (byte) mul(result[j] & 0xFF, root);
                if (j + 1 < result.length) result[j] ^= result[j + 1];
            }
            root = mul(root, 0x02);
        }
        return result;
    }

    private static byte[] rsRemainder(byte[] data, byte[] divisor) {
        byte[] result = new byte[divisor.length];
        for (byte b : data) {
            int factor = (b ^ result[0]) & 0xFF;
            System.arraycopy(result, 1, result, 0, result.length - 1);
            result[result.length - 1] = 0;
            for (int i = 0; i < result.length; i++) result[i] ^= mul(divisor[i] & 0xFF, factor);
        }
        return result;
    }

    private static int mul(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z;
    }

    private static boolean bit(int x, int i) { return ((x >>> i) & 1) != 0; }

    private static void appendBits(List<Boolean> bb, int val, int len) {
        for (int i = len - 1; i >= 0; i--) bb.add(((val >>> i) & 1) != 0);
    }
}
