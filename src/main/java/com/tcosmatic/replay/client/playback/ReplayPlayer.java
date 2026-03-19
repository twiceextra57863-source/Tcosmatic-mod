package com.tcosmatic.replay.client.playback;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import com.tcosmatic.replay.TcosmaticReplayMod;
import com.tcosmatic.replay.common.ReplayData;
import com.tcosmatic.replay.client.utils.CompressionUtil;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class ReplayPlayer {
    private final MinecraftClient client;
    private ReplayData currentReplay;
    private PlaybackState state = PlaybackState.STOPPED;
    private long currentFrame = 0;
    private long totalFrames = 0;
    private float playbackSpeed = 1.0f;
    private CameraPath cameraPath;
    private KeyframeSystem keyframeSystem;
    private Map<Integer, EntitySnapshot> entitySnapshots;
    private boolean isLooping = false;
    private long startTime;
    
    public enum PlaybackState {
        STOPPED, PLAYING, PAUSED, SEEKING
    }
    
    public ReplayPlayer() {
        this.client = MinecraftClient.getInstance();
        this.keyframeSystem = new KeyframeSystem();
        this.cameraPath = new CameraPath();
        this.entitySnapshots = new HashMap<>();
    }
    
    public CompletableFuture<Boolean> loadReplay(String replayId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TcosmaticReplayMod.LOGGER.info("Loading replay: {}", replayId);
                
                // Load compressed replay data
                byte[] compressed = CompressionUtil.loadReplayFile(replayId);
                currentReplay = CompressionUtil.decompressReplay(compressed);
                
                if (currentReplay != null) {
                    totalFrames = currentReplay.getFrameCount();
                    currentFrame = 0;
                    
                    // Pre-cache first few frames
                    preCacheFrames(0, Math.min(100, (int)totalFrames));
                    
                    TcosmaticReplayMod.LOGGER.info("Replay loaded: {} frames", totalFrames);
                    return true;
                }
            } catch (Exception e) {
                TcosmaticReplayMod.LOGGER.error("Failed to load replay", e);
            }
            return false;
        });
    }
    
    private void preCacheFrames(int start, int count) {
        // Pre-load frames for smooth playback
        for (int i = start; i < start + count && i < totalFrames; i++) {
            currentReplay.getFrame(i); // This caches in memory
        }
    }
    
    public void play() {
        if (currentReplay == null) return;
        
        state = PlaybackState.PLAYING;
        startTime = System.currentTimeMillis() - (long)(currentFrame * 50 / playbackSpeed);
        
        TcosmaticReplayMod.LOGGER.info("Playback started at frame {}", currentFrame);
    }
    
    public void pause() {
        if (state == PlaybackState.PLAYING) {
            state = PlaybackState.PAUSED;
            TcosmaticReplayMod.LOGGER.info("Playback paused at frame {}", currentFrame);
        }
    }
    
    public void resume() {
        if (state == PlaybackState.PAUSED) {
            state = PlaybackState.PLAYING;
            startTime = System.currentTimeMillis() - (long)(currentFrame * 50 / playbackSpeed);
            TcosmaticReplayMod.LOGGER.info("Playback resumed");
        }
    }
    
    public void stop() {
        state = PlaybackState.STOPPED;
        currentFrame = 0;
        clearWorld();
        TcosmaticReplayMod.LOGGER.info("Playback stopped");
    }
    
    public void seekToFrame(long frame) {
        if (frame < 0) frame = 0;
        if (frame >= totalFrames) frame = totalFrames - 1;
        
        state = PlaybackState.SEEKING;
        currentFrame = frame;
        
        // Apply frame immediately
        applyFrame(currentReplay.getFrame((int)frame));
        
        state = PlaybackState.PAUSED;
        TcosmaticReplayMod.LOGGER.info("Seeked to frame {}/{}", frame, totalFrames);
    }
    
    public void seekToTime(float seconds) {
        long frame = (long)(seconds * 20); // 20 FPS
        seekToFrame(frame);
    }
    
    public void updatePlayback() {
        if (state != PlaybackState.PLAYING || currentReplay == null) return;
        
        long expectedFrame = (long)((System.currentTimeMillis() - startTime) * playbackSpeed / 50);
        
        if (expectedFrame >= totalFrames) {
            if (isLooping) {
                currentFrame = 0;
                startTime = System.currentTimeMillis();
                expectedFrame = 0;
            } else {
                stop();
                return;
            }
        }
        
        if (expectedFrame != currentFrame) {
            currentFrame = expectedFrame;
            applyFrame(currentReplay.getFrame((int)currentFrame));
        }
    }
    
    private void applyFrame(ReplayData.FrameData frame) {
        if (client.world == null) return;
        
        // Apply camera position (if not using custom path)
        if (!cameraPath.isActive()) {
            applyCameraPosition(frame);
        } else {
            applyCameraPath(currentFrame);
        }
        
        // Apply entity positions
        applyEntities(frame);
        
        // Apply block changes
        applyBlockChanges(frame);
    }
    
    private void applyCameraPosition(ReplayData.FrameData frame) {
        if (client.player == null) return;
        
        client.player.setPosition(frame.playerX, frame.playerY, frame.playerZ);
        client.player.setYaw(frame.playerYaw);
        client.player.setPitch(frame.playerPitch);
        
        // Update camera entity
        if (client.cameraEntity != null) {
            client.cameraEntity.setPosition(frame.playerX, frame.playerY, frame.playerZ);
            client.cameraEntity.setYaw(frame.playerYaw);
            client.cameraEntity.setPitch(frame.playerPitch);
        }
    }
    
    private void applyCameraPath(long frame) {
        CameraPath.Keyframe keyframe = cameraPath.getKeyframeAtFrame(frame);
        if (keyframe != null && client.player != null) {
            client.player.setPosition(keyframe.x, keyframe.y, keyframe.z);
            client.player.setYaw(keyframe.yaw);
            client.player.setPitch(keyframe.pitch);
        }
    }
    
    private void applyEntities(ReplayData.FrameData frame) {
        if (client.world == null) return;
        
        // Update existing entities
        for (Map.Entry<Integer, ReplayData.EntityData> entry : frame.entities.entrySet()) {
            Entity entity = client.world.getEntityById(entry.getKey());
            if (entity != null) {
                ReplayData.EntityData data = entry.getValue();
                entity.setPosition(data.x, data.y, data.z);
                entity.setVelocity(data.motionX, data.motionY, data.motionZ);
                entity.setYaw(data.yaw);
                entity.setPitch(data.pitch);
            }
        }
    }
    
    private void applyBlockChanges(ReplayData.FrameData frame) {
        if (client.world == null) return;
        
        for (ReplayData.BlockChange change : frame.blockChanges) {
            client.world.setBlockState(change.pos, change.state, 0);
        }
    }
    
    private void clearWorld() {
        if (client.world == null) return;
        
        // Reset to original state
        // Implementation depends on how we store original world state
    }
    
    public void addKeyframe() {
        if (client.player != null) {
            cameraPath.addKeyframe(
                currentFrame,
                client.player.getX(),
                client.player.getY(),
                client.player.getZ(),
                client.player.getYaw(),
                client.player.getPitch()
            );
        }
    }
    
    public float getPlaybackProgress() {
        if (totalFrames == 0) return 0;
        return (float)currentFrame / totalFrames;
    }
    
    // Getters and setters
    public PlaybackState getState() { return state; }
    public long getCurrentFrame() { return currentFrame; }
    public long getTotalFrames() { return totalFrames; }
    public float getPlaybackSpeed() { return playbackSpeed; }
    public void setPlaybackSpeed(float speed) { this.playbackSpeed = speed; }
    public void setLooping(boolean looping) { this.isLooping = looping; }
    public CameraPath getCameraPath() { return cameraPath; }
    public KeyframeSystem getKeyframeSystem() { return keyframeSystem; }
    
    // Entity snapshot for smooth transitions
    static class EntitySnapshot {
        Vec3d position;
        Vec3d rotation;
        Vec3d motion;
    }
            }
