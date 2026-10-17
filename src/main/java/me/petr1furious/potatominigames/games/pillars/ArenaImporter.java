package me.petr1furious.potatominigames.games.pillars;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Cuts one arena out of a vanilla world: copies the chunks within a radius of the
 * arena's center, at their original coordinates, into a world folder of their own,
 * which ASP can then turn into that arena's slime template.
 *
 * <p>Works on the region files directly (block, entity and POI regions alike), so
 * it neither depends on the world being loaded nor on anything version-specific
 * inside the chunks.
 */
final class ArenaImporter {
    private static final int SECTOR = 4096;
    private static final Pattern REGION = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

    private ArenaImporter() {
    }

    /**
     * @return how many block chunks were copied; 0 means the arena has nothing in it
     */
    static int cut(File sourceWorld, File target, double centerX, double centerZ, int radius) throws IOException {
        int copied = 0;
        for (String kind : new String[] {"region", "entities", "poi"}) {
            File from = new File(sourceWorld, kind);
            File[] files = from.listFiles((dir, name) -> REGION.matcher(name).matches());
            if (files == null) {
                continue;
            }
            for (File region : files) {
                int count = cutRegion(region, new File(target, kind), centerX, centerZ, radius);
                if (kind.equals("region")) {
                    copied += count;
                }
            }
        }
        return copied;
    }

    private static int cutRegion(File region, File targetFolder, double centerX, double centerZ, int radius)
            throws IOException {
        Matcher matcher = REGION.matcher(region.getName());
        if (!matcher.matches()) {
            return 0;
        }
        int regionX = Integer.parseInt(matcher.group(1));
        int regionZ = Integer.parseInt(matcher.group(2));
        byte[] data = Files.readAllBytes(region.toPath());
        if (data.length < 2 * SECTOR) {
            return 0;
        }
        ByteBuffer header = ByteBuffer.wrap(data, 0, 2 * SECTOR);

        // First pass: which chunks of this region belong to the arena.
        byte[][] payloads = new byte[1024][];
        int[] timestamps = new int[1024];
        int kept = 0;
        for (int index = 0; index < 1024; index++) {
            int location = header.getInt(index * 4);
            int offset = (location >>> 8) & 0xFFFFFF;
            int sectors = location & 0xFF;
            if (offset == 0 || sectors == 0) {
                continue;
            }
            int chunkX = regionX * 32 + (index % 32);
            int chunkZ = regionZ * 32 + (index / 32);
            // The chunk's nearest point to the center decides, so a radius never cuts an arena in half.
            double nearestX = Math.max(chunkX * 16, Math.min(centerX, chunkX * 16 + 15));
            double nearestZ = Math.max(chunkZ * 16, Math.min(centerZ, chunkZ * 16 + 15));
            if (Math.hypot(nearestX - centerX, nearestZ - centerZ) > radius) {
                continue;
            }
            int start = offset * SECTOR;
            if (start + 5 > data.length) {
                continue;
            }
            int length = ByteBuffer.wrap(data, start, 4).getInt();
            if (length <= 0 || start + 4 + length > data.length) {
                continue;
            }
            byte[] payload = new byte[4 + length];
            System.arraycopy(data, start, payload, 0, payload.length);
            payloads[index] = payload;
            timestamps[index] = header.getInt(SECTOR + index * 4);
            kept++;
            // An oversized chunk keeps its data in a file next to the region.
            if ((payload[4] & 0x80) != 0) {
                File external = new File(region.getParentFile(), "c." + chunkX + "." + chunkZ + ".mcc");
                if (external.isFile()) {
                    targetFolder.mkdirs();
                    Files.copy(external.toPath(), new File(targetFolder, external.getName()).toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        if (kept == 0) {
            return 0;
        }

        // Second pass: write a region holding only those chunks, packed from sector 2.
        targetFolder.mkdirs();
        try (RandomAccessFile out = new RandomAccessFile(new File(targetFolder, region.getName()), "rw")) {
            out.setLength(0);
            out.write(new byte[2 * SECTOR]);
            int sector = 2;
            for (int index = 0; index < 1024; index++) {
                byte[] payload = payloads[index];
                if (payload == null) {
                    continue;
                }
                int sectors = (payload.length + SECTOR - 1) / SECTOR;
                out.seek((long) sector * SECTOR);
                out.write(payload);
                out.write(new byte[sectors * SECTOR - payload.length]);
                out.seek(index * 4L);
                out.writeInt((sector << 8) | (sectors & 0xFF));
                out.seek(SECTOR + index * 4L);
                out.writeInt(timestamps[index]);
                sector += sectors;
            }
        }
        return kept;
    }

    static void deleteRecursively(File folder) throws IOException {
        if (!folder.exists()) {
            return;
        }
        try (Stream<Path> paths = Files.walk(folder.toPath())) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
