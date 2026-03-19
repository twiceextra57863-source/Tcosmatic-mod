package com.tcosmatic.replay.client.recording;

import com.tcosmatic.replay.TcosmaticReplayMod;
import com.tcosmatic.replay.common.ReplayData;
import com.tcosmatic.replay.common.ReplayMetadata;
import com.tcosmatic.replay.client.utils.CompressionUtil;
import com.tcosmatic.replay.client.utils.FileManager;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class RecordingSession {
    private final String sessionId;
    private final String worldName;
    private final long startTime;
    private long endTime;
    
    private final Queue<ReplayRecorder.FrameCapture> frameBuffer;
    private final AtomicInteger frameCount;
    private final Map<String, Object> sessionData;
    
    private Path tempFile;
    private Path finalFile;
    private boolean isSaved = false;
    private boolean isCorrupted = false;
    
    // Performance stats
    private int minFrameSize = Integer.MAX_VALUE;
    private int maxFrameSize = 0;
    private long totalDataSize = 0;
    
    public RecordingSession(String worldName) {
        this.sessionId = UUID.randomUUID().toString();
        this.worldName = worldName;
        this.startTime = System.currentTimeMillis();
        this.frameBuffer = new ConcurrentLinkedQueue<>();
        this.frameCount = new AtomicInteger(0);
        this.sessionData = new HashMap<>();
        
        // Create temp file
        createTempFile();
        
        TcosmaticReplayMod.LOGGER.info("📁 Recording session created: {}", sessionId);
    }
    
    private void createTempFile() {
        try {
            Path tempDir = Paths.get("Tcosmatic/temp");
            Files.createDirectories(tempDir);
            
            tempFile = tempDir.resolve(sessionId + ".tmp");
            finalFile = Paths.get("Tcosmatic/replays/" + sessionId + ".tcos");
            
            TcosmaticReplayMod.LOGGER.debug("Temp file: {}", tempFile);
            
        } catch (IOException e) {
            TcosmaticReplayMod.LOGGER.error("Failed to create temp file", e);
        }
    }
    
    public void addFrame(ReplayRecorder.FrameCapture frame) {
        frameBuffer.offer(frame);
        int count = frameCount.incrementAndGet();
        
        // Update stats
        if (frame.compressedData != null) {
            int size = frame.compressedData.length;
            minFrameSize = Math.min(minFrameSize, size);
            maxFrameSize = Math.max(maxFrameSize, size);
            totalDataSize += size;
        }
        
        // Auto-save every 500 frames
        if (count % 500 == 0) {
            saveBatchToTemp();
        }
    }
    
    public void addFrames(List<ReplayRecorder.FrameCapture> frames) {
        for (ReplayRecorder.FrameCapture frame : frames) {
            addFrame(frame);
        }
    }
    
    private void saveBatchToTemp() {
        if (frameBuffer.isEmpty()) return;
        
        try {
            List<ReplayRecorder.FrameCapture> batch = new ArrayList<>();
            frameBuffer.drainTo(batch);
            
            // Convert to ReplayData frames
            List<ReplayData.FrameData> replayFrames = convertToReplayData(batch);
            
            // Append to temp file
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new BufferedOutputStream(
                    Files.newOutputStream(tempFile, 
                    StandardOpenOption.CREATE, 
                    StandardOpenOption.APPEND)))) {
                
                oos.writeObject(replayFrames);
                oos.flush();
            }
            
            TcosmaticReplayMod.LOGGER.debug("Saved {} frames to temp", batch.size());
            
        } catch (IOException e) {
            TcosmaticReplayMod.LOGGER.error("Failed to save batch to temp", e);
            isCorrupted = true;
        }
    }
    
    private List<ReplayData.FrameData> convertToReplayData(List<ReplayRecorder.FrameCapture> captures) {
        List<ReplayData.FrameData> frames = new ArrayList<>();
        
        for (ReplayRecorder.FrameCapture capture : captures) {
            ReplayData.FrameData frame = new ReplayData.FrameData();
            
            // Basic info
            frame.timestamp = capture.timestamp;
            
            // Player data
            if (capture.player != null) {
                frame.playerX = (float)capture.player.x;
                frame.playerY = (float)capture.player.y;
                frame.playerZ = (float)capture.player.z;
                frame.playerYaw = capture.player.yaw;
                frame.playerPitch = capture.player.pitch;
                frame.playerHeadYaw = capture.player.headYaw;
            }
            
            // Entity data
            if (capture.entities != null) {
                for (ReplayRecorder.EntitySnapshot entity : capture.entities) {
                    ReplayData.EntityData ed = new ReplayData.EntityData();
                    ed.entityId = entity.id;
                    ed.entityType = entity.type;
                    ed.x = (float)entity.x;
                    ed.y = (float)entity.y;
                    ed.z = (float)entity.z;
                    ed.yaw = entity.yaw;
                    ed.pitch = entity.pitch;
                    ed.motionX = (float)entity.velocity.x;
                    ed.motionY = (float)entity.velocity.y;
                    ed.motionZ = (float)entity.velocity.z;
                    frame.entities.put(entity.id, ed);
                }
            }
            
            // World state
            frame.worldTime = capture.worldTime;
            frame.raining = capture.raining;
            frame.thundering = capture.thundering;
            
            frames.add(frame);
        }
        
        return frames;
    }
    
    public void saveToDisk() {
        saveBatchToTemp(); // Save any remaining frames
    }
    
    public void finalizeAndSave() {
        endTime = System.currentTimeMillis();
        saveBatchToTemp(); // Final save
        
        try {
            TcosmaticReplayMod.LOGGER.info("Finalizing recording session...");
            
            // Read all frames from temp
            List<ReplayData.FrameData> allFrames = new ArrayList<>();
            
            try (ObjectInputStream ois = new ObjectInputStream(
                    Files.newInputStream(tempFile))) {
                
                while (true) {
                    try {
                        List<ReplayData.FrameData> frames = 
                            (List<ReplayData.FrameData>) ois.readObject();
                        allFrames.addAll(frames);
                    } catch (EOFException e) {
                        break; // End of file
                    }
                }
            }
            
            // Create ReplayData object
            ReplayData replayData = new ReplayData();
            replayData.setReplayId(sessionId);
            for (ReplayData.FrameData frame : allFrames) {
                replayData.addFrame(frame);
            }
            
            // Compress and save
            byte[] compressed = CompressionUtil.compressReplay(replayData);
            Files.write(finalFile, compressed);
            
            // Clean up temp file
            Files.deleteIfExists(tempFile);
            
            isSaved = true;
            
            TcosmaticReplayMod.LOGGER.info("✅ Recording saved: {} frames, {:.2f} MB", 
                allFrames.size(), compressed.length / (1024.0 * 1024.0));
            
        } catch (IOException | ClassNotFoundException e) {
            TcosmaticReplayMod.LOGGER.error("Failed to finalize recording", e);
            isCorrupted = true;
        }
    }
    
    public ReplayMetadata createMetadata() {
        ReplayMetadata metadata = new ReplayMetadata();
        metadata.setId(sessionId);
        metadata.setName("Recording " + new Date(startTime));
        metadata.setWorldName(worldName);
        metadata.setTimestamp(startTime);
        metadata.setDuration(endTime - startTime);
        metadata.setFrameCount(frameCount.get());
        metadata.setFileSize((int)totalDataSize);
        metadata.setGameVersion("1.21");
        metadata.setModVersion("1.0.0");
        metadata.setCorrupted(isCorrupted);
        
        // Add performance stats
        metadata.getCustomData().put("minFrameSize", minFrameSize);
        metadata.getCustomData().put("maxFrameSize", maxFrameSize);
        metadata.getCustomData().put("avgFrameSize", totalDataSize / frameCount.get());
        metadata.getCustomData().put("totalDataSize", totalDataSize);
        
        return metadata;
    }
    
    public void abort() {
        try {
            Files.deleteIfExists(tempFile);
            Files.deleteIfExists(finalFile);
        } catch (IOException e) {
            TcosmaticReplayMod.LOGGER.error("Failed to clean up session files", e);
        }
    }
    
    // Getters
    public String getSessionId() { return sessionId; }
    public int getFrameCount() { return frameCount.get(); }
    public long getDuration() { return endTime - startTime; }
    public boolean isSaved() { return isSaved; }
    public boolean isCorrupted() { return isCorrupted; }
  }
