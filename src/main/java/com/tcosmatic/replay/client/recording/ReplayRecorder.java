package com.tcosmatic.replay.client.recording;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import com.tcosmatic.replay.common.ReplayData;
import com.tcosmatic.replay.common.ReplayMetadata;
import com.tcosmatic.replay.client.utils.CompressionUtil;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ReplayRecorder {
    private final MinecraftClient client;
    private final Queue<FrameData> frameQueue = new ConcurrentLinkedQueue<>();
    private RecordingSession currentSession;
    private boolean isRecording = false;
    private boolean isPaused = false;
    private Thread recordingThread;
    
    public ReplayRecorder() {
        this.client = MinecraftClient.getInstance();
    }
    
    public void startRecording() {
        currentSession = new RecordingSession();
        isRecording = true;
        isPaused = false;
        
        recordingThread = new Thread(() -> {
            while (isRecording) {
                if (!isPaused) {
                    captureFrame();
                }
                try {
                    Thread.sleep(50); // 20 FPS recording
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        recordingThread.start();
    }
    
    private void captureFrame() {
        if (client.player == null || client.world == null) return;
        
        FrameData frame = new FrameData();
        frame.timestamp = System.currentTimeMillis();
        frame.playerPos = client.player.getPos();
        frame.playerRot = client.player.getRotationVector();
        frame.playerPitch = client.player.getPitch();
        frame.playerYaw = client.player.getYaw();
        
        // Capture block changes
        frame.blockChanges = captureBlockChanges();
        
        // Capture entities
        frame.entities = captureEntities();
        
        frameQueue.offer(frame);
        
        // Auto-save every 1000 frames
        if (frameQueue.size() >= 1000) {
            saveBatch();
        }
    }
    
    private void saveBatch() {
        List<FrameData> batch = new ArrayList<>();
        while (!frameQueue.isEmpty() && batch.size() < 1000) {
            batch.add(frameQueue.poll());
        }
        
        if (!batch.isEmpty()) {
            CompressionUtil.compressAndSave(batch, currentSession);
        }
    }
    
    public void stopRecording() {
        isRecording = false;
        try {
            recordingThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        // Save remaining frames
        saveBatch();
        
        // Save metadata
        currentSession.saveMetadata();
    }
    
    // Inner class for frame data
    static class FrameData {
        long timestamp;
        Vec3d playerPos;
        Vec3d playerRot;
        float playerPitch;
        float playerYaw;
        List<BlockChange> blockChanges;
        List<EntityData> entities;
    }
    
    static class BlockChange {
        int x, y, z;
        int oldBlock, newBlock;
    }
    
    static class EntityData {
        int id;
        Vec3d pos;
        Vec3d velocity;
    }
}
