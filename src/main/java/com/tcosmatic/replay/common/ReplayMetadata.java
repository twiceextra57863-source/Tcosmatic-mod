package com.tcosmatic.replay.common;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.*;

public class ReplayMetadata implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String id;
    private String name;
    private String worldName;
    private String gameVersion;
    private String modVersion;
    private long timestamp;
    private long duration;
    private int frameCount;
    private int fileSize;
    private String previewPath;
    private Map<String, Object> customData;
    private List<String> tags;
    private boolean isCorrupted;
    
    public ReplayMetadata() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
        this.modVersion = "1.0.0";
        this.customData = new HashMap<>();
        this.tags = new ArrayList<>();
        this.isCorrupted = false;
    }
    
    public ReplayMetadata(String name, String worldName) {
        this();
        this.name = name;
        this.worldName = worldName;
    }
    
    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getWorldName() { return worldName; }
    public void setWorldName(String worldName) { this.worldName = worldName; }
    
    public String getGameVersion() { return gameVersion; }
    public void setGameVersion(String gameVersion) { this.gameVersion = gameVersion; }
    
    public String getModVersion() { return modVersion; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = duration; }
    
    public int getFrameCount() { return frameCount; }
    public void setFrameCount(int frameCount) { this.frameCount = frameCount; }
    
    public int getFileSize() { return fileSize; }
    public void setFileSize(int fileSize) { this.fileSize = fileSize; }
    
    public String getPreviewPath() { return previewPath; }
    public void setPreviewPath(String previewPath) { this.previewPath = previewPath; }
    
    public Map<String, Object> getCustomData() { return customData; }
    
    public List<String> getTags() { return tags; }
    public void addTag(String tag) { tags.add(tag); }
    
    public boolean isCorrupted() { return isCorrupted; }
    public void setCorrupted(boolean corrupted) { this.isCorrupted = corrupted; }
    
    // Utility methods
    public String getFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new Date(timestamp));
    }
    
    public String getFormattedDuration() {
        long seconds = duration / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60);
        } else {
            return String.format("%02d:%02d", minutes, seconds % 60);
        }
    }
    
    public String getFileSizeFormatted() {
        if (fileSize < 1024) {
            return fileSize + " B";
        } else if (fileSize < 1024 * 1024) {
            return String.format("%.2f KB", fileSize / 1024.0);
        } else {
            return String.format("%.2f MB", fileSize / (1024.0 * 1024.0));
        }
    }
    
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("world", worldName);
        map.put("date", getFormattedDate());
        map.put("duration", getFormattedDuration());
        map.put("frames", frameCount);
        map.put("size", getFileSizeFormatted());
        map.put("version", gameVersion);
        return map;
    }
}
