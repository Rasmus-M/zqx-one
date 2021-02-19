import java.io.FileInputStream;
import java.util.HashSet;
import java.util.Set;

public class Count implements Runnable {

    private static final int WINDOW_WIDTH = 29;
    private static final int MAP_HEIGHT = 26;
    private static final int[] MAP_WIDTHS = {228, 224};

    public static void main(String... args) {
        for (int level = 1; level <= 1; level++) {
            new Count(level, "level" + level + ".mgb", MAP_WIDTHS[level - 1], MAP_HEIGHT).run();
        }
    }

    private final int level;
    private final String fileName;
    private final int width;
    private final int height;

    private Count(int level, String fileName, int width, int height) {
        this.level = level;
        this.fileName = fileName;
        this.width = width;
        this.height = height;
    }

    public void run() {
        try {
            System.out.println("****************");
            System.out.println("Map " + fileName);
            System.out.println("****************");
            FileInputStream fis = new FileInputStream(this.fileName);
            byte[] buffer = new byte[0x4000];
            int len = fis.read(buffer);
            fis.close();
            int[][] map = new int[height][width];
            int n = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    map[y][x] = ((buffer[n] & 0xff) << 8) | (buffer[n + 1] & 0xff);
                    n += 2;
                }
            }
            int screen = 1;
            for (int x0 = -(WINDOW_WIDTH - 1); x0 <= width; x0++) {
                Set<Integer> charSet = new HashSet<>();
                for (int y = 0; y < height; y++) {
                    for (int x = x0; x < x0 + WINDOW_WIDTH; x++) {
                        int ch = getMapChar(map, x, y);
                        charSet.add(ch);
                    }
                }
                System.out.println(screen + ": " + charSet.size() + (charSet.size() > 256 ? " *****" : ""));
                screen++;
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private int getMapChar(int[][] map, int x, int y) {
        return x < 0 || x >= map[0].length ? 0 : map[y][x];
    }

    private String hexWord(int w) {
        StringBuilder s = new StringBuilder(Integer.toHexString(w));
        while (s.length() < 4) {
            s.insert(0, "0");
        }
        return ">" + s.toString();
    }
}

