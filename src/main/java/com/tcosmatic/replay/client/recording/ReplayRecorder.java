package com.tcosmatic.replay.client.recording;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import com.tcosmatic.replay.TcosmaticReplayMod;
import com.tcosmatic.replay.common.ReplayData;
import com.tcosmatic.replay.common.ReplayMetadata;
import com.tcosmatic.replay.client.utils.FileManager;
import com.tcosmatic.replay.client.utils.CompressionUtil;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ReplayRecorder {
    private static ReplayRecorder instance;
    private final MinecraftClient client;
    private final ExecutorService recordingExecutor;
    private final ScheduledExecutorService scheduler;
    
    // Recording state
    private RecordingSession currentSession;
    private AtomicBoolean isRecording = new AtomicBoolean(false);
    private AtomicBoolean isPaused = new AtomicBoolean(false);
    private AtomicInteger frameCounter = new AtomicInteger(0);
    
    // Queues for multi-threaded processing
    private final BlockingQueue<FrameCapture> frameQueue;
    private final BlockingQueue<FrameCapture> compressionQueue;
    private final BlockingQueue<FrameCapture> saveQueue;
    
    // Performance monitoring
    private long lastFrameTime;
    private float currentFPS = 20.0f;
    private int droppedFrames = 0;
    
    // Cache for optimized recording
    private final Map<Integer, EntitySnapshot> lastEntitySnapshot = new ConcurrentHashMap<>();
    private final Map<BlockPos, BlockState> lastBlockStates = new ConcurrentHashMap<>();
    private PlayerSnapshot lastPlayerSnapshot;
    
    private ReplayRecorder() {
        this.client = MinecraftClient.getInstance();
        
        // Initialize thread pools
        this.recordingExecutor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "Tcosmatic-Recorder");
            t.setDaemon(true);
            return t;
        });
        
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "Tcosmatic-Scheduler");
            t.setDaemon(true);
            return t;
        });
        
        // Initialize queues with capacity limits
        this.frameQueue = new LinkedBlockingQueue<>(500);  // Max 500 frames in memory
        this.compressionQueue = new LinkedBlockingQueue<>(200);
        this.saveQueue = new LinkedBlockingQueue<>(100);
        
        TcosmaticReplayMod.LOGGER.info("🎥 ReplayRecorder initialized");
    }
    
    public static ReplayRecorder getInstance() {
        if (instance == null) {
            instance = new ReplayRecorder();
        }
        return instance;
    }
    
    public void startRecording(String worldName) {
        if (isRecording.get()) {
            TcosmaticReplayMod.LOGGER.warn("Already recording!");
            return;
        }
        
        try {
            // Create new recording session
            currentSession = new RecordingSession(worldName);
            frameCounter.set(0);
            droppedFrames = 0;
            lastFrameTime = System.currentTimeMillis();
            
            // Clear caches
            lastEntitySnapshot.clear();
            lastBlockStates.clear();
            lastPlayerSnapshot = null;
            
            // Start recording threads
            startCaptureThread();
            startCompressionThread();
            startSaveThread();
            
            // Start monitoring thread
            startMonitoringThread();
            
            isRecording.set(true);
            isPaused.set(false);
            
            TcosmaticReplayMod.LOGGER.info("▶️ Recording started in world: {}", worldName);
            
        } catch (Exception e) {
            TcosmaticReplayMod.LOGGER.error("Failed to start recording", e);
        }
    }
    
    private void startCaptureThread() {
        recordingExecutor.submit(() -> {
            TcosmaticReplayMod.LOGGER.info("Capture thread started");
            
            while (isRecording.get()) {
                try {
                    if (!isPaused.get() && client.world != null && client.player != null) {
                        long now = System.currentTimeMillis();
                        long frameTime = now - lastFrameTime;
                        
                        // Target 20 FPS (50ms per frame)
                        if (frameTime >= 50) {
                            FrameCapture capture = captureFrame();
                            if (capture != null) {
                                if (!frameQueue.offer(capture, 100, TimeUnit.MILLISECONDS)) {
                                    droppedFrames++;
                                    TcosmaticReplayMod.LOGGER.warn("Frame queue full, dropped frame. Total dropped: {}", droppedFrames);
                                }
                            }
                            lastFrameTime = now;
                        }
                    }
                    
                    // Small sleep to prevent CPU hogging
                    Thread.sleep(5);
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    TcosmaticReplayMod.LOGGER.error("Error in capture thread", e);
                }
            }
            
            TcosmaticReplayMod.LOGGER.info("Capture thread stopped");
        });
    }
    
    private void startCompressionThread() {
        recordingExecutor.submit(() -> {
            TcosmaticReplayMod.LOGGER.info("Compression thread started");
            
            while (isRecording.get() || !frameQueue.isEmpty()) {
                try {
                    FrameCapture capture = frameQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (capture != null) {
                        // Compress frame data
                        capture.compressedData = compressFrameData(capture);
                        compressionQueue.offer(capture);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            TcosmaticReplayMod.LOGGER.info("Compression thread stopped");
        });
    }
    
    private void startSaveThread() {
        recordingExecutor.submit(() -> {
            TcosmaticReplayMod.LOGGER.info("Save thread started");
            
            List<FrameCapture> batch = new ArrayList<>();
            
            while (isRecording.get() || !compressionQueue.isEmpty()) {
                try {
                    FrameCapture capture = compressionQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (capture != null) {
                        batch.add(capture);
                        
                        // Save in batches of 50 frames
                        if (batch.size() >= 50) {
                            saveBatch(batch);
                            batch.clear();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            
            // Save remaining frames
            if (!batch.isEmpty()) {
                saveBatch(batch);
            }
            
            TcosmaticReplayMod.LOGGER.info("Save thread stopped");
        });
    }
    
    private void startMonitoringThread() {
        scheduler.scheduleAtFixedRate(() -> {
            if (isRecording.get()) {
                // Update FPS calculation
                int framesThisSecond = frameCounter.getAndSet(0);
                currentFPS = framesThisSecond;
                
                // Log performance stats
                if (droppedFrames > 0) {
                    TcosmaticReplayMod.LOGGER.debug("Recording stats - FPS: {:.1f}, Dropped: {}, Queue sizes: {}/{}/{}",
                        currentFPS, droppedFrames, frameQueue.size(), compressionQueue.size(), saveQueue.size());
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
    }
    
    private FrameCapture captureFrame() {
        try {
            FrameCapture capture = new FrameCapture();
            capture.timestamp = System.currentTimeMillis();
            capture.frameNumber = frameCounter.incrementAndGet();
            
            // Capture player data
            capture.player = capturePlayerSnapshot();
            
            // Capture entity changes (only changed entities)
            capture.entities = captureEntityChanges();
            
            // Capture block changes (only changed blocks)
            capture.blockChanges = captureBlockChanges();
            
            // Capture world time/weather
            capture.worldTime = client.world.getTime();
            capture.raining = client.world.isRaining();
            capture.thundering = client.world.isThundering();
            
            return capture;
            
        } catch (Exception e) {
            TcosmaticReplayMod.LOGGER.error("Failed to capture frame", e);
            return null;
        }
    }
    
    private PlayerSnapshot capturePlayerSnapshot() {
        if (client.player == null) return null;
        
        PlayerSnapshot snapshot = new PlayerSnapshot();
        snapshot.x = client.player.getX();
        snapshot.y = client.player.getY();
        snapshot.z = client.player.getZ();
        snapshot.yaw = client.player.getYaw();
        snapshot.pitch = client.player.getPitch();
        snapshot.headYaw = client.player.headYaw;
        snapshot.health = client.player.getHealth();
        snapshot.foodLevel = client.player.getHungerManager().getFoodLevel();
        snapshot.sprinting = client.player.isSprinting();
        snapshot.sneaking = client.player.isSneaking();
        snapshot.swimming = client.player.isSwimming();
        snapshot.falling = client.player.getVelocity().y < -0.5;
        snapshot.velocity = client.player.getVelocity();
        
        // Capture main hand item
        if (client.player.getMainHandStack() != null) {
            snapshot.mainHandItem = Registries.ITEM.getId(
                client.player.getMainHandStack().getItem()).toString();
        }
        
        return snapshot;
    }
    
    private List<EntitySnapshot> captureEntityChanges() {
        List<EntitySnapshot> changes = new ArrayList<>();
        if (client.world == null) return changes;
        
        for (Entity entity : client.world.getEntities()) {
            // Skip players (already captured separately)
            if (entity instanceof PlayerEntity) continue;
            
            int id = entity.getId();
            EntitySnapshot current = new EntitySnapshot(entity);
            EntitySnapshot last = lastEntitySnapshot.get(id);
            
            // Check if entity moved or changed significantly
            if (shouldCaptureEntity(current, last)) {
                changes.add(current);
                lastEntitySnapshot.put(id, current);
            }
        }
        
        // Clean up dead entities
        lastEntitySnapshot.keySet().removeIf(id -> client.world.getEntityById(id) == null);
        
        return changes;
    }
    
    private boolean shouldCaptureEntity(EntitySnapshot current, EntitySnapshot last) {
        if (last == null) return true;
        
        // Check position change (threshold 0.1 blocks)
        double dx = current.x - last.x;
        double dy = current.y - last.y;
        double dz = current.z - last.z;
        if (dx*dx + dy*dy + dz*dz > 0.01) return true;
        
        // Check rotation change (threshold 5 degrees)
        if (Math.abs(current.yaw - last.yaw) > 5 || 
            Math.abs(current.pitch - last.pitch) > 5) return true;
        
        return false;
    }
    
    private List<BlockChange> captureBlockChanges() {
        List<BlockChange> changes = new ArrayList<>();
        if (client.world == null || client.interactionManager == null) return changes;
        
        // This would be integrated with a block change listener
        // For now, we'll track changes via a simple cache
        
        return changes;
    }
    
    private byte[] compressFrameData(FrameCapture capture) {
        try {
            // Simple serialization for now
            // In production, use Protocol Buffers or similar
            return CompressionUtil.compressFrameData(capture);
        } catch (Exception e) {
            TcosmaticReplayMod.LOGGER.error("Failed to compress frame", e);
            return new byte[0];
        }
    }
    
    private void saveBatch(List<FrameCapture> batch) {
        try {
            currentSession.addFrames(batch);
            
            // Auto-save every 1000 frames
            if (currentSession.getFrameCount() % 1000 == 0) {
                currentSession.saveToDisk();
            }
            
        } catch (Exception e) {
            TcosmaticReplayMod.LOGGER.error("Failed to save batch", e);
        }
    }
    
    public void pauseRecording() {
        if (isRecording.get() && !isPaused.get()) {
            isPaused.set(true);
            TcosmaticReplayMod.LOGGER.info("⏸️ Recording paused at frame {}", frameCounter.get());
        }
    }
    
    public void resumeRecording() {
        if (isRecording.get() && isPaused.get()) {
            isPaused.set(false);
            lastFrameTime = System.currentTimeMillis(); // Reset timing
            TcosmaticReplayMod.LOGGER.info("▶️ Recording resumed");
        }
    }
    
    public void stopRecording() {
        if (!isRecording.get()) return;
        
        TcosmaticReplayMod.LOGGER.info("⏹️ Stopping recording...");
        isRecording.set(false);
        
        try {
            // Wait for queues to empty (max 5 seconds)
            int timeout = 0;
            while ((!frameQueue.isEmpty() || !compressionQueue.isEmpty()) && timeout < 50) {
                Thread.sleep(100);
                timeout++;
            }
            
            // Final save
            if (currentSession != null) {
                currentSession.finalizeAndSave();
                
                // Create metadata
                ReplayMetadata metadata = currentSession.createMetadata();
                FileManager.getInstance().saveMetadata(metadata);
                
                TcosmaticReplayMod.LOGGER.info("✅ Recording saved: {} frames, {} dropped", 
                    frameCounter.get(), droppedFrames);
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Shutdown threads
        recordingExecutor.shutdown();
        scheduler.shutdown();
    }
    
    public boolean isRecording() { return isRecording.get(); }
    public boolean isPaused() { return isPaused.get(); }
    public int getFrameCount() { return frameCounter.get(); }
    public float getCurrentFPS() { return currentFPS; }
    public int getDroppedFrames() { return droppedFrames; }
    
    // Inner classes for data structures
    public static class FrameCapture {
        public int frameNumber;
        public long timestamp;
        public PlayerSnapshot player;
        public List<EntitySnapshot> entities;
        public List<BlockChange> blockChanges;
        public long worldTime;
        public boolean raining;
        public boolean thundering;
        public byte[] compressedData;
    }
    
    public static class PlayerSnapshot {
        public double x, y, z;
        public float yaw, pitch, headYaw;
        public float health;
        public int foodLevel;
        public boolean sprinting, sneaking, swimming, falling;
        public Vec3d velocity;
        public String mainHandItem;
    }
    
    public static class EntitySnapshot {
        public int id;
        public String type;
        public double x, y, z;
        public float yaw, pitch;
        public Vec3d velocity;
        
        public EntitySnapshot(Entity entity) {
            this.id = entity.getId();
            this.type = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
            this.x = entity.getX();
            this.y = entity.getY();
            this.z = entity.getZ();
            this.yaw = entity.getYaw();
            this.pitch = entity.getPitch();
            this.velocity = entity.getVelocity();
        }
    }
    
    public static class BlockChange {
        public BlockPos pos;
        public int oldState;
        public int newState;
    }
                            }
