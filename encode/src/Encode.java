import java.io.FileInputStream;
import java.io.FileWriter;
import java.util.*;

/**
 * Created by Rasmus on 16-02-2021.
 */
public class Encode implements Runnable {

    private static final boolean VERBOSE = false;
    private static final int WINDOW_WIDTH = 28;
    private static final int MAP_HEIGHT = 26;
    private static final int[] MAP_WIDTHS = {259, 224};

    private final int level;
    private final String fileName;
    private final int width;
    private final int height;

    public static void main(String... args) {
        for (int level = 1; level <= 1; level++) {
            new Encode(level, "level" + level + ".mgb", MAP_WIDTHS[level - 1], MAP_HEIGHT).run();
        }
    }

    private Encode(int level, String fileName, int width, int height) {
        this.level = level;
        this.fileName = fileName;
        this.width = width;
        this.height = height;
    }

    @Override
    public void run() {
        try {
            System.out.println("****************");
            System.out.println("Map " + fileName);
            System.out.println("****************");
            FileInputStream fis = new FileInputStream(this.fileName);
            byte[] buffer = new byte[0x4000];
            int len = fis.read(buffer);
            fis.close();
            if (len == width * height * 2) {
                System.out.println(len + " bytes loaded.");
                // Process map
                int[][] map = new int[height][width];
                int n = 0;
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        map[y][x] = ((buffer[n] & 0xff) << 8) | (buffer[n + 1] & 0xff);
                        n += 2;
                    }
                }
                StringBuilder out = new StringBuilder();
                StringBuilder diff_out = new StringBuilder();
                int[][] newMap = new int[height][width + WINDOW_WIDTH];
                Integer[] runningChars = new Integer[256];
                int maxSize = 0;
                int maxIndex = 0;
                int screen = 0;
                for (int x0 = 0; x0 < width; x0++) {
                    if (VERBOSE) System.out.println("Screen " + screen + ":");
                    Set<Integer> used = new HashSet<>();
                    Map<Integer, Integer> added = new HashMap<>();
                    Set<Integer> deleted = new HashSet<>();
                    // Find chars used in screen
                    for (int y = 0; y < height; y++) {
                        for (int x = x0; x < x0 + WINDOW_WIDTH; x++) {
                            int ch = x < width ? map[y][x] : 0;
                            used.add(ch);
                        }
                    }
                    if (VERBOSE) System.out.println("Used: " + used.size());
                    // Process screen
                    for (int y = 0; y < height; y++) {
                        for (int x = x0; x < x0 + WINDOW_WIDTH; x++) {
                            int ch = x < width ? map[y][x] : 0;
                            // Is char in current set?
                            Integer runningIndex = null;
                            for (int i = 0; i < runningChars.length && runningIndex == null; i++) {
                                Integer globalIndex = runningChars[i];
                                if (globalIndex != null && globalIndex == ch) {
                                    runningIndex = i;
                                }
                            }
                            // If not, add it
                            if (runningIndex == null) {
                                for (int i = 0; i < runningChars.length && runningIndex == null; i++) {
                                    Integer oldGlobalIndex = runningChars[i];
                                    if (oldGlobalIndex == null || !used.contains(oldGlobalIndex)) {
                                        runningIndex = i;
                                        Integer globalIndex = ch;
                                        runningChars[runningIndex] = globalIndex;
                                        maxIndex = Math.max(maxIndex, runningIndex);
                                        added.put(runningIndex, globalIndex);
                                        used.add(globalIndex);
                                    }
                                }
                            }
                            // Record in map
                            if (runningIndex != null) {
                                newMap[y][x] = runningIndex;
                            } else {
                                throw new Exception("No room found for key " + hexWord(ch));
                            }
                        }
                    }
                    int iMax = 0;
                    for (int i = 0; i < runningChars.length; i++) {
                        Integer globalIndex = runningChars[i];
                        if (globalIndex != null && !used.contains(globalIndex)) {
                            runningChars[i] = null;
                            deleted.add(i);
                        }
                        if (runningChars[i] != null) {
                            iMax = i;
                        }
                    }
                    maxSize = Math.max(maxSize, used.size());

                    if (VERBOSE) System.out.print("Add: ");
                    diff_out.append("level_" + level + "_" + to3Digits(screen) + "_add:\n");
                    diff_out.append("       byte " + hexByte(added.size()) + "\n");
                    for (Integer i : added.keySet()) {
                        int globalIndex = added.get(i);
                        if (VERBOSE) System.out.print(hexByte(i) + ":" + hexWord(globalIndex) + " ");
                        diff_out.append("       byte " + hexByte(i) + ", " + hexByte(globalIndex >> 8) + ", " + hexByte(globalIndex & 0xff) + "              ; " + hexWord(globalIndex) + "\n");
                    }
                    if (VERBOSE) System.out.println();

                    if (VERBOSE) System.out.print("Delete: ");
                    diff_out.append("level_" + level + "_" + to3Digits(screen) + "_delete:\n");
                    diff_out.append("       byte " + hexByte(deleted.size()) + "\n");
                    for (Integer i : deleted) {
                        if (VERBOSE) System.out.print(hexByte(i) + " ");
                        diff_out.append("       byte " + hexByte(i) + "\n");
                    }
                    if (VERBOSE) System.out.println();

                    if (VERBOSE) System.out.println("Deleted: " + deleted.size() + ", Added: " + added.size() + ", Used = "+ used.size() + " of " + (iMax + 1));
                    screen++;
                }
                if (VERBOSE) System.out.println("Map:");
                out.append("*******************************************\n");
                out.append("level_" + level + "_map:\n");
                for (int y = 0; y < newMap.length; y++) {
                    int[] row = newMap[y];
                    out.append("       byte ");
                    for (int x = 0; x < row.length; x++) {
                        if (VERBOSE) System.out.print(hexByte(row[x]));
                        out.append(hexByte(row[x])).append(x < row.length - 1 ? "," : "\n");
                    }
                    if (VERBOSE) System.out.println();
                }
                out.append("*******************************************\n");
                out.append("level_" + level + "_max:\n");
                out.append("       byte " + hexByte(maxIndex) + "\n");
                out.append(diff_out);

                if (VERBOSE) System.out.println();
                System.out.println("Max size: " + maxSize);
                System.out.println("Max index: " + maxIndex);
                System.out.println();

                // Write output
                FileWriter writer = new FileWriter("../src/level" + level + ".a99");
                writer.write(out.toString());
                writer.close();
            } else {
                throw new Exception("Error: " + len + " bytes found. Expected " + (width * height) + " bytes.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private String to3Digits(int i) {
        StringBuilder s = new StringBuilder(Integer.toString(i));
        while (s.length() < 3) {
            s.insert(0, "0");
        }
        return s.toString();
    }

    private String hexByte(int b) {
        if (b < 256) {
            StringBuilder s = new StringBuilder(Integer.toHexString(b));
            while (s.length() < 2) {
                s.insert(0, "0");
            }
            return ">" + s.toString();
        } else {
            return "!!";
        }
    }

    private String hexWord(int w) {
        StringBuilder s = new StringBuilder(Integer.toHexString(w));
        while (s.length() < 4) {
            s.insert(0, "0");
        }
        return ">" + s.toString();
    }
}
