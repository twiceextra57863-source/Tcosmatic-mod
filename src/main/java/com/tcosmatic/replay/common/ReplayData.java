package com.tcosmatic.replay.common;

import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;
import java.io.Serializable;
import java.util.*;

public class ReplayData implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String replayId;
    private String worldName;
    private long recordingStartTime;
    private long recordingEndTime;
    private int version = 1;
    private int frameCount;
    private List<FrameData> frames;
    private Map<String, Object> metadata;
    private transient int uncompressedSize = 0;
    
    public ReplayData() {
        this.frames = new ArrayList<>();
        this.metadata = new HashMap<>();
        this.replayId = UUID.randomUUID().toString();
    }
    
    public void addFrame(FrameData frame) {
        frames.add(frame);
        frameCount++;
    }
    
    public FrameData getFrame(int index) {
        return frames.get(index);
    }
    
    public List<FrameData> getFrames() { return frames; }
    public int getFrameCount() { return frameCount; }
    public String getReplayId() { return replayId; }
    public void setReplayId(String id) { this.replayId = id; }
    
    public int getUncompressedSize() {
        if (uncompressedSize == 0) {
            // Rough estimate
            uncompressedSize = frames.size() * 200; // Average frame size
        }
        return uncompressedSize;
    }
    
    public static class FrameData implements Serializable {
        private static final long serialVersionUID = 1L;
        
        public long timestamp;
        public float playerX, playerY, playerZ;
        public float playerYaw, playerPitch;
        public float cameraYaw, cameraPitch;
        public Map<Integer, EntityData> entities;
        public List<BlockChange> blockChanges;
        public Map<String, Object> customData;
        
        public FrameData() {
            this.entities = new HashMap<>();
            this.blockChanges = new ArrayList<>();
            this.customData = new HashMap<>();
        }
    }
    
    public static class EntityData implements Serializable {
        private static final long serialVersionUID = 1L;
        
        public int entityId;
        public String entityType;
        public float x, y, z;
        public float yaw, pitch;
        public float motionX, motionY, motionZ;
        public Map<String, Object> attributes;
        
        public EntityData() {
            this.attributes = new HashMap<>();
        }
    }
    
    public static class BlockChange implements Serializable {
        private static final long serialVersionUID = 1L;
        
        public int x, y, z;
        public int oldState;
        public int newState;
        public transient BlockPos pos;
        
        public BlockChange(int x, int y, int z, int oldState, int newState) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.oldState = oldState;
            this.newState = newState;
            this.pos = new BlockPos(x, y, z);
        }
    }
    
    public static class PlayerSnapshot implements Serializable {
        private static final long serialVersionUID = 1L;
        
        public float x, y, z;
        public float yaw, pitch;
        public float headYaw;
        public float health;
        public int foodLevel;
        public int itemInHand;
        public Map<Integer, Integer> inventory;
        
        public PlayerSnapshot() {
            this.inventory = new HashMap<>();
        }
    }
}
