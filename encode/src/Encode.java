import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.*;
import java.util.*;
import java.util.List;

/**
 * Created by Rasmus on 16-02-2021.
 */
public class Encode implements Runnable {

    private static final boolean VERBOSE = true;
    private static final int WINDOW_WIDTH = 29;
    private static final int MAP_HEIGHT = 26;
    private static final int[] MAP_WIDTHS = {128, 228, 192};
    private static final int[] TILES = {43, 702, 499};
    private static final int MAX_CHARS = 228;
    private static final boolean WRITE_DIFF = false;
    private static final boolean WRITE_BINARY_MAP = true;

    private final int level;
    private final String fileName;
    private final int width;
    private final int height;
    private final int tiles;

    public static void main(String... args) {
        for (int level = 0; level <= 2; level++) {
            new Encode(level, "level" + level + ".mgb", MAP_WIDTHS[level], MAP_HEIGHT, TILES[level]).run();
        }
    }

    private Encode(int level, String fileName, int width, int height, int tiles) {
        this.level = level;
        this.fileName = fileName;
        this.width = width;
        this.height = height;
        this.tiles = tiles;
    }

    @Override
    public void run() {
        try {
            System.out.println("* * * * * * * * * * * * * * * *");
            System.out.println("Map " + fileName);
            System.out.println("* * * * * * * * * * * * * * * *");

            // Read files
            FileInputStream fis = new FileInputStream(this.fileName);
            byte[] buffer = new byte[0x8000];
            int len = fis.read(buffer);
            fis.close();
            int expectedLen = width * height;
            if (len != expectedLen && len != expectedLen * 2) {
                throw new Exception("Error: " + len + " bytes found. Expected " + expectedLen + " or " + (expectedLen * 2)  + " bytes.");
            }
            System.out.println(len + " bytes loaded.");
            boolean doubleByte = len == expectedLen * 2;
            // Read tile patterns
            int[][][] tilePatterns = readTilePatterns("tile-patterns-" + level + ".png");

            // Process map
            int[][] map = readMap(buffer, doubleByte);
            StringBuilder mapOut = new StringBuilder();
            StringBuilder diffOut = new StringBuilder();
            int[][] newMap = new int[height][width + WINDOW_WIDTH];
            Integer[] runningChars = new Integer[MAX_CHARS];
            List<int[]> replacements = new ArrayList<>();
            int maxSize = 0;
            int screen = 0;
            for (int x0 = -(WINDOW_WIDTH - 1); x0 <= width - WINDOW_WIDTH; x0++) {

                if (VERBOSE) System.out.println("Screen " + screen + ":");

                // Find chars used on screen
                Set<Integer> usedOnScreen = getUsedOnScreen(newMap, x0 + WINDOW_WIDTH);

                // Delete unused chars
                Set<Integer> deleted = deleteUnused(runningChars, usedOnScreen);

                Map<Integer, Integer> added = new HashMap<>();
                for (int y = height - 1; y >= 0; y--) { // Backwards to favor bottom of screen
                    int x = x0 + WINDOW_WIDTH - 1;
                    int mapChar = getMapChar(map, x, y);
                    // Is char in current set?
                    Integer runningIndex = findExisting(mapChar, runningChars);
                    // If not, add it if there is space
                    if (runningIndex == null) {
                        runningIndex = addIfSpace(mapChar, runningChars);
                        if (runningIndex != null) {
                            added.put(runningIndex, mapChar);
                        }
                    }
                    // If not, find best match in current set
                    if (runningIndex == null) {
                        runningIndex = findClosestTilePattern(mapChar, runningChars, tilePatterns);
                        if (VERBOSE) {
                            int globalIndex = runningChars[runningIndex];
                            System.out.println("*** Pattern " + mapChar + " replaced by " + globalIndex + " (" + runningIndex + ")");
                            recordReplacement(mapChar, globalIndex, replacements);
                        }
                    }
                    // Record in map
                    setMapChar(newMap, x + WINDOW_WIDTH, y, runningIndex);
                }

                // Record added
                if (VERBOSE) System.out.print("Add: ");
                diffOut.append("level_").append(level).append("_").append(to3Digits(screen)).append("_add:\n");
                diffOut.append("       byte ").append(hexByte(added.size())).append("\n");
                for (Integer i : added.keySet()) {
                    int globalIndex = added.get(i);
                    if (VERBOSE) System.out.print(hexByte(i) + ":" + hexWord(globalIndex) + " ");
                    diffOut.append("       byte ").append(hexByte(i)).append(", ").append(hexByte(globalIndex >> 8)).append(", ").append(hexByte(globalIndex & 0xff)).append("              ; ").append(hexWord(globalIndex)).append("\n");
                }
                if (VERBOSE) System.out.println();

                // Record deleted
                if (VERBOSE) System.out.print("Delete: ");
                if (WRITE_DIFF) {
                    diffOut.append("level_").append(level).append("_").append(to3Digits(screen)).append("_delete:\n");
                    diffOut.append("       byte ").append(hexByte(deleted.size())).append("\n");
                }
                for (Integer i : deleted) {
                    if (VERBOSE) System.out.print(hexByte(i) + " ");
                    if (WRITE_DIFF) {
                        diffOut.append("       byte ").append(hexByte(i)).append("\n");
                    }
                }
                int size = countUsed(runningChars);
                maxSize = Math.max(maxSize, size);
                if (VERBOSE) System.out.println();
                if (VERBOSE) System.out.println("Deleted: " + deleted.size() + ", Added: " + added.size() + ", Used = "+ size + " of " + MAX_CHARS + ". Max index=" + maxUsedIndex(runningChars));
                screen++;
            }

            // Write diff
            FileWriter writer = new FileWriter("../src/diff" + level + ".a99");
            writer.write(diffOut.toString());
            writer.close();

            // Generate assembly map
            if (VERBOSE) System.out.println("Map:");
            mapOut.append("*******************************************\n");
            mapOut.append("level_").append(level).append("_map:\n");
            for (int[] row : newMap) {
                mapOut.append("       byte ");
                for (int x = WINDOW_WIDTH; x < row.length; x++) {
                    if (VERBOSE) System.out.print(hexByte(row[x]));
                    mapOut.append(hexByte(row[x])).append(x < row.length - 1 ? "," : "\n");
                }
                if (VERBOSE) System.out.println();
            }

            // Write map
            if (WRITE_BINARY_MAP) {
                FileOutputStream fos = new FileOutputStream("../bin/map" + level + ".bin");
                for (int[] row : newMap) {
                    for (int x = WINDOW_WIDTH; x < row.length; x++) {
                        fos.write(row[x]);
                    }
                }
                fos.close();
            } else {
                FileWriter writer2 = new FileWriter("../src/map" + level + ".a99");
                writer2.write(mapOut.toString());
                writer2.close();
            }

            if (VERBOSE) System.out.println();
            System.out.println("Max size: " + maxSize);
            System.out.println();

            // Write replacements images
            if (VERBOSE && replacements.size() > 0) {
                writeReplacementsImage(replacements, tilePatterns);
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private int[][] readMap(byte[] buffer, boolean doubleByte) {
        int[][] map = new int[height][width];
        int n = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (doubleByte) {
                    map[y][x] = ((buffer[n] & 0xff) << 8) | (buffer[n + 1] & 0xff);
                    n += 2;
                } else {
                    map[y][x] = (buffer[n] & 0xff);
                    n ++;
                }
            }
        }
        return map;
    }

    private Set<Integer> deleteUnused(Integer[] runningChars, Set<Integer> used) {
        Set<Integer> deleted = new HashSet<>();
        for (int i = 0; i < runningChars.length; i++) {
            if (runningChars[i] != null && !used.contains(i)) {
                runningChars[i] = null;
                deleted.add(i);
            }
        }
        return deleted;
    }

    private Set<Integer> getUsedOnScreen(int[][] map, int x0) {
        Set<Integer> used = new HashSet<>();
        for (int y = 0; y < height; y++) {
            for (int x = x0; x < x0 + WINDOW_WIDTH; x++) {
                used.add(map[y][x]);
            }
        }
        return used;
    }

    private Integer findExisting(int mapChar, Integer[] runningChars) {
        Integer runningIndex = null;
        for (int i = 0; i < runningChars.length && runningIndex == null; i++) {
            Integer globalIndex = runningChars[i];
            if (globalIndex != null && globalIndex == mapChar) {
                runningIndex = i;
            }
        }
        return runningIndex;
    }

    private Integer addIfSpace(int mapChar, Integer[] runningChars) {
        Integer runningIndex = null;
        for (int i = 0; i < runningChars.length && runningIndex == null; i++) {
            if (runningChars[i] == null) {
                runningIndex = i;
                runningChars[runningIndex] = mapChar;
            }
        }
        return runningIndex;
    }

    private void recordReplacement(int mapChar, int globalIndex, List<int[]> replacements) {
        boolean found = false;
        for (int[] pair : replacements) {
            if (pair[0] == mapChar && pair[1] == globalIndex) {
                found = true;
                break;
            }
        }
        if (!found) {
            replacements.add(new int[] {mapChar, globalIndex});
        }
    }

    private int countUsed(Integer[] runningChars) {
        int used = 0;
        for (Integer c : runningChars) {
            if (c != null) {
                used++;
            }
        }
        return used;
    }

    private int maxUsedIndex(Integer[] runningChars) {
        int iMax = 0;
        for (int i = 0; i < runningChars.length; i++) {
            if (runningChars[i] != null) {
                iMax = i;
            }
        }
        return iMax;
    }

    private int[][][] readTilePatterns(String filename) throws IOException {
        int[][][] patterns = new int[tiles][8][8];
        BufferedImage image = ImageIO.read(new File(filename));
        int i = 0;
        for (int y0 = 0; y0 < image.getHeight(); y0 += 8) {
            for (int x0 = 0; x0 < image.getWidth(); x0 += 8) {
                if (i < tiles) {
                    int[][] pattern = patterns[i];
                    for (int y = 0; y < 8; y++) {
                        for (int x = 0; x < 8; x++) {
                            pattern[y][x] = image.getRGB(x0 + x, y0 + y);
                        }
                    }
                    i++;
                }
            }
        }
        return patterns;
    }

    private int getMapChar(int[][] map, int x, int y) {
        return x < 0 || x >= map[0].length ? 0 : map[y][x];
    }

    private void setMapChar(int[][] map, int x, int y, int c) throws Exception {
        if (x >= 0 && x < map[y].length) {
            map[y][x] = c;
        } else {
            throw new Exception("Index out of range: " + x);
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
            return ">" + s;
        } else {
            return "!!";
        }
    }

    private String hexWord(int w) {
        StringBuilder s = new StringBuilder(Integer.toHexString(w));
        while (s.length() < 4) {
            s.insert(0, "0");
        }
        return ">" + s;
    }

    private int findClosestTilePattern(int n, Integer[] runningChars, int[][][] tilePatterns) {
        int[][] tilePattern = tilePatterns[n];
        int closest = 0;
        double minDist = Double.MAX_VALUE;
        for (int i = 0; i < runningChars.length; i++) {
            Integer ch = runningChars[i];
            if (ch != null) {
                double dist = gridColorDistance(tilePattern, tilePatterns[ch]);
                if (dist < minDist) {
                    minDist = dist;
                    closest = i;
                }
            }
        }
        return closest;
    }

    private double gridColorDistance(int[][] grid1, int[][] grid2) {
        double dist = 0;
        for (int y = 0; y < grid1.length; y++) {
            for (int x = 0; x < grid1[0].length; x++) {
                dist += colorDistance(grid1[y][x], grid2[y][x]);
            }
        }
        return dist;
    }

    private double colorDistance(int color1, int color2) {
        return colorDistance(new Color(color1, true), new Color(color2, true));
    }

    private double colorDistance(Color c1, Color c2) {
        return Math.sqrt(Math.pow(c1.getRed() - c2.getRed(), 2) + Math.pow(c1.getGreen() - c2.getGreen(), 2) + Math.pow(c1.getBlue() - c2.getBlue(), 2));
    }

    private void writeReplacementsImage(List<int[]> replacements, int[][][] tilePatterns) throws IOException {
        BufferedImage image = new BufferedImage(17, replacements.size() * 9 - 1, BufferedImage.TYPE_INT_ARGB);
        WritableRaster raster = image.getRaster();
        int y0 = 0;
        for (int[] pair : replacements) {
            writePatternToRaster(raster, 0, y0, tilePatterns[pair[0]]);
            writePatternToRaster(raster, 9, y0, tilePatterns[pair[1]]);
            y0 += 9;
        }
        ImageIO.write(image, "png", new File("replacements" + level + ".png"));
    }

    private void writePatternToRaster(WritableRaster raster, int x0, int y0, int[][] pattern) {
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                Color color = new Color(pattern[y][x]);
                int[] pixel = new int[] {color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()};
                raster.setPixel(x + x0, y + y0, pixel);
            }
        }
    }
}
