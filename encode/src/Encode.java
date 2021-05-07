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
    private static final int[] MAP_WIDTHS = {228, 224};
    private static final int[] TILES = {702, 0};
    private static final int MAX_CHARS = 250;

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
            byte[] buffer = new byte[0x8000];
            int len = fis.read(buffer);
            fis.close();
            if (len == width * height * 2) {
                System.out.println(len + " bytes loaded.");
                // Read tile patterns
                int[][][] tilePatterns = readTilePatterns("tile-patterns-" + level + ".png");
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
                Integer[] runningChars = new Integer[MAX_CHARS];
                List<int[]> replacements = new ArrayList<>();
                int maxSize = 0;
                int maxIndex = 0;
                int screen = 0;
                for (int x0 = -(WINDOW_WIDTH - 1); x0 <= width - WINDOW_WIDTH; x0++) {
                    // Process screen
                    if (VERBOSE) System.out.println("Screen " + screen + ":");
                    Map<Integer, Integer> added = new HashMap<>();
                    for (int y = height - 1; y >= 0; y--) { // Backwards to favor bottom of screen
                        int x = x0 + WINDOW_WIDTH - 1;
                        int ch = getMapChar(map, x, y);
                        // Is char in current set?
                        Integer runningIndex = null;
                        for (int i = 0; i < runningChars.length && runningIndex == null; i++) {
                            Integer globalIndex = runningChars[i];
                            if (globalIndex != null && globalIndex == ch) {
                                runningIndex = i;
                            }
                        }
                        // If not, add it if there is space
                        if (runningIndex == null) {
                            for (int i = 0; i < runningChars.length && runningIndex == null; i++) {
                                Integer oldGlobalIndex = runningChars[i];
                                if (oldGlobalIndex == null) {
                                    runningIndex = i;
                                    Integer globalIndex = ch;
                                    runningChars[runningIndex] = globalIndex;
                                    maxIndex = Math.max(maxIndex, runningIndex);
                                    added.put(runningIndex, globalIndex);
                                }
                            }
                        }
                        // If not, find best match in current set
                        if (runningIndex == null) {
                            runningIndex = findClosestTilePattern(ch, runningChars, tilePatterns);
                            int globalIndex = runningChars[runningIndex];
                            if (VERBOSE) {
                                System.out.println("*** Pattern " + globalIndex + " (" + runningIndex + ") selected instead of " + ch);
                                System.out.println("x=" + x + " y=" + y);
                                boolean found = false;
                                for (int[] pair : replacements) {
                                    if (pair[0] == ch && pair[1] == globalIndex) {
                                        found = true;
                                        break;
                                    }
                                }
                                if (!found) {
                                    replacements.add(new int[] {ch, globalIndex});
                                }
                            }
                        }
                        // Record in map
                        int ix = x + WINDOW_WIDTH;
                        if (ix >= 0 && ix < newMap[y].length) {
                            newMap[y][ix] = runningIndex;
                            if (x==157 && y==24) {
                                System.out.println("Write " + runningIndex + " (" + runningChars[runningIndex] + ") to x="+ix + ", y=" + y);
                            }
                        } else {
                            throw new Exception("Index out of range: " + ix);
                        }
                    }

                    // Record added
                    if (VERBOSE) System.out.print("Add: ");
                    diff_out.append("level_").append(level).append("_").append(to3Digits(screen)).append("_add:\n");
                    diff_out.append("       byte ").append(hexByte(added.size())).append("\n");
                    for (Integer i : added.keySet()) {
                        int globalIndex = added.get(i);
                        if (VERBOSE) System.out.print(hexByte(i) + ":" + hexWord(globalIndex) + " ");
                        diff_out.append("       byte ").append(hexByte(i)).append(", ").append(hexByte(globalIndex >> 8)).append(", ").append(hexByte(globalIndex & 0xff)).append("              ; ").append(hexWord(globalIndex)).append("\n");
                    }
                    if (VERBOSE) System.out.println();

                    // Find chars used on screen
                    Set<Integer> used = new HashSet<>();
                    int newX0 = x0 + WINDOW_WIDTH;
                    for (int y = 0; y < height; y++) {
                        for (int x = newX0; x < newX0 + WINDOW_WIDTH; x++) {
                            used.add(newMap[y][x]);
                        }
                    }
                    if (VERBOSE) System.out.println("Used: " + used.size());

                    // Delete unused chars
                    Set<Integer> deleted = new HashSet<>();
                    for (int i = 0; i < runningChars.length; i++) {
                        if (!used.contains(i)) {
                            runningChars[i] = null;
                            deleted.add(i);
                        }
                    }

                    // Record deleted
                    if (VERBOSE) System.out.print("Delete: ");
//                    diff_out.append("level_" + level + "_" + to3Digits(screen) + "_delete:\n");
//                    diff_out.append("       byte " + hexByte(deleted.size()) + "\n");
                    for (Integer i : deleted) {
                        if (VERBOSE) System.out.print(hexByte(i) + " ");
//                        diff_out.append("       byte " + hexByte(i) + "\n");
                    }
                    if (VERBOSE) System.out.println();

                    // Find max used index
                    int iMax = 0;
                    for (int i = 0; i < runningChars.length; i++) {
                        if (runningChars[i] != null) {
                            iMax = i;
                        }
                    }
                    maxSize = Math.max(maxSize, countUsed(runningChars));

                    if (VERBOSE) System.out.println("Deleted: " + deleted.size() + ", Added: " + added.size() + ", Used = "+ used.size() + " of " + (iMax + 1));
                    screen++;
                }
                // Write output
                FileWriter writer = new FileWriter("../src/diff" + level + ".a99");
                writer.write(diff_out.toString());
                writer.close();

                if (VERBOSE) System.out.println("Map:");
                out.append("*******************************************\n");
                out.append("level_").append(level).append("_map:\n");
                for (int[] row : newMap) {
                    out.append("       byte ");
                    for (int x = WINDOW_WIDTH; x < row.length; x++) {
                        if (VERBOSE) System.out.print(hexByte(row[x]));
                        out.append(hexByte(row[x])).append(x < row.length - 1 ? "," : "\n");
                    }
                    if (VERBOSE) System.out.println();
                }
                // Write output
//                FileWriter writer2 = new FileWriter("../src/map" + level + ".a99");
//                writer2.write(out.toString());
//                writer2.close();
                FileOutputStream fos = new FileOutputStream("../bin/map" + level + ".bin");
                for (int[] row : newMap) {
                    for (int x = WINDOW_WIDTH; x < row.length; x++) {
                        fos.write(row[x]);
                    }
                }
                fos.close();

                if (VERBOSE) System.out.println();
                System.out.println("Max size: " + maxSize);
                System.out.println("Max index: " + maxIndex);
                System.out.println("Map width: " + (newMap[0].length - WINDOW_WIDTH));
                System.out.println();

                if (VERBOSE && replacements.size() > 0) {
                    BufferedImage image = new BufferedImage(17, replacements.size() * 9 - 1, BufferedImage.TYPE_INT_ARGB);
                    WritableRaster raster = image.getRaster();
                    int y0 = 0;
                    for (int[] pair : replacements) {
                        writePatternToRaster(raster, 0, y0, tilePatterns[pair[0]]);
                        writePatternToRaster(raster, 9, y0, tilePatterns[pair[1]]);
                        y0 += 9;
                    }
                    ImageIO.write(image, "png", new File("replacements.png"));
                }
            } else {
                throw new Exception("Error: " + len + " bytes found. Expected " + (width * height) + " bytes.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
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

    private int[][][] readTilePatterns(String filename) throws IOException {
        int nTiles = TILES[level - 1];
        int[][][] patterns = new int[nTiles][8][8];
        BufferedImage image = ImageIO.read(new File(filename));
        int i = 0;
        for (int y0 = 0; y0 < image.getHeight(); y0 += 8) {
            for (int x0 = 0; x0 < image.getWidth(); x0 += 8) {
                if (i < nTiles) {
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
