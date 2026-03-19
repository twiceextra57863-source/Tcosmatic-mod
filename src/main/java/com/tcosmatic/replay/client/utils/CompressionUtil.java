package com.tcosmatic.replay.client.utils;

import com.tcosmatic.replay.TcosmaticReplayMod;
import com.tcosmatic.replay.common.ReplayData;
import java.io.*;
import java.util.zip.*;
import java.nio.file.*;
import java.util.*;

public class CompressionUtil {
    private static final int BUFFER_SIZE = 8192;
    private static final int COMPRESSION_LEVEL = 9; // Best compression
    
    // Compress and save replay data
    public static byte[] compressReplay(ReplayData replay) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        // Use GZIP for better compression
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos) {
            { def.setLevel(COMPRESSION_LEVEL); }
        }) {
            // Write magic number
            gzip.write("TCOS".getBytes());
            
            // Write version
            gzip.write(1);
            
            // Write replay data
            ObjectOutputStream oos = new ObjectOutputStream(gzip);
            oos.writeObject(replay);
            oos.flush();
        }
        
        byte[] compressed = baos.toByteArray();
        TcosmaticReplayMod.LOGGER.info("Compressed replay: {} -> {} bytes ({}% reduction)", 
            replay.getUncompressedSize(), compressed.length,
            (100 - (compressed.length * 100 / replay.getUncompressedSize())));
        
        return compressed;
    }
    
    // Decompress replay data
    public static ReplayData decompressReplay(byte[] data) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             GZIPInputStream gzip = new GZIPInputStream(bais)) {
            
            // Verify magic number
            byte[] magic = new byte[4];
            gzip.read(magic);
            if (!new String(magic).equals("TCOS")) {
                throw new IOException("Invalid replay file format");
            }
            
            // Read version
            int version = gzip.read();
            
            // Read replay data
            ObjectInputStream ois = new ObjectInputStream(gzip);
            return (ReplayData) ois.readObject();
        }
    }
    
    // Save replay to file
    public static void saveReplayFile(ReplayData replay, String filename) throws IOException {
        Path path = Paths.get("Tcosmatic/replays/" + filename + ".tcos");
        Files.createDirectories(path.getParent());
        
        byte[] compressed = compressReplay(replay);
        Files.write(path, compressed);
        
        TcosmaticReplayMod.LOGGER.info("Saved replay to: {}", path);
    }
    
    // Load replay from file
    public static ReplayData loadReplayFile(String filename) throws IOException, ClassNotFoundException {
        Path path = Paths.get("Tcosmatic/replays/" + filename);
        
        if (!Files.exists(path)) {
            path = Paths.get("Tcosmatic/replays/" + filename + ".tcos");
        }
        
        byte[] compressed = Files.readAllBytes(path);
        return decompressReplay(compressed);
    }
    
    // Delta compression for frame data
    public static byte[] compressFrameDelta(ReplayData.FrameData base, ReplayData.FrameData delta) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(baos))) {
            // Position delta
            dos.writeFloat(delta.playerX - base.playerX);
            dos.writeFloat(delta.playerY - base.playerY);
            dos.writeFloat(delta.playerZ - base.playerZ);
            
            // Rotation delta
            dos.writeFloat(delta.playerYaw - base.playerYaw);
            dos.writeFloat(delta.playerPitch - base.playerPitch);
            
            // Entity changes (only changed entities)
            dos.writeInt(delta.entities.size());
            for (Map.Entry<Integer, ReplayData.EntityData> entry : delta.entities.entrySet()) {
                dos.writeInt(entry.getKey()); // Entity ID
                ReplayData.EntityData ed = entry.getValue();
                ReplayData.EntityData baseEd = base.entities.get(entry.getKey());
                
                if (baseEd != null) {
                    // Delta compression for entities
                    dos.writeFloat(ed.x - baseEd.x);
                    dos.writeFloat(ed.y - baseEd.y);
                    dos.writeFloat(ed.z - baseEd.z);
                    dos.writeFloat(ed.yaw - baseEd.yaw);
                    dos.writeFloat(ed.pitch - baseEd.pitch);
                } else {
                    // Full entity data
                    dos.writeFloat(ed.x);
                    dos.writeFloat(ed.y);
                    dos.writeFloat(ed.z);
                    dos.writeFloat(ed.yaw);
                    dos.writeFloat(ed.pitch);
                }
            }
            
            // Block changes (only changed blocks)
            dos.writeInt(delta.blockChanges.size());
            for (ReplayData.BlockChange bc : delta.blockChanges) {
                dos.writeInt(bc.x);
                dos.writeInt(bc.y);
                dos.writeInt(bc.z);
                dos.writeInt(bc.oldState);
                dos.writeInt(bc.newState);
            }
        }
        
        return baos.toByteArray();
    }
    
    // Run-length encoding for block changes
    public static byte[] rleCompress(int[] data) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        
        try {
            int count = 1;
            int current = data[0];
            
            for (int i = 1; i < data.length; i++) {
                if (data[i] == current && count < 65535) {
                    count++;
                } else {
                    dos.writeInt(current);
                    dos.writeShort(count);
                    current = data[i];
                    count = 1;
                }
            }
            
            dos.writeInt(current);
            dos.writeShort(count);
            dos.flush();
            
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        return baos.toByteArray();
    }
    
    // Multi-threaded compression for large files
    public static CompletableFuture<byte[]> compressAsync(ReplayData replay) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return compressReplay(replay);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
    
    // Verify replay file integrity
    public static boolean verifyReplayFile(String filename) {
        try {
            Path path = Paths.get("Tcosmatic/replays/" + filename);
            if (!Files.exists(path)) {
                path = Paths.get("Tcosmatic/replays/" + filename + ".tcos");
            }
            
            byte[] data = Files.readAllBytes(path);
            
            // Check magic number
            if (data.length < 4) return false;
            String magic = new String(data, 0, 4);
            if (!magic.equals("TCOS")) return false;
            
            // Try to decompress
            decompressReplay(data);
            return true;
            
        } catch (Exception e) {
            TcosmaticReplayMod.LOGGER.error("Replay file corrupted: {}", filename, e);
            return false;
        }
    }
}
