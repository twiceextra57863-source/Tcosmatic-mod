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
import net.minecraft.world.chunk.WorldChunk;
import com.tcosmatic.replay.TcosmaticReplayMod;
import com.tcosmatic.replay.common.ReplayData;
import com.tcosmatic.replay.common.ReplayMetadata;
import com.tcosmatic.replay.client.utils.FileManager;
import com.tcosmatic.replay.client.utils.CompressionUtil;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.*;

public class ReplayRecorder {
    private static ReplayRecorder instance;
    private final MinecraftClient client;
    
    // Thread pools for parallel processing
    private final ExecutorService captureExecutor;
    private final ExecutorService compressionExecutor;
    private final ExecutorService saveExecutor;
    private final ScheduledExecutorService monitorExecutor;
    
    // Queues for pipeline processing
    private final BlockingQueue<RawFrame> rawFrameQueue;
    private final BlockingQueue<ProcessedFrame> compressedFrameQueue;
    private final BlockingQueue<FrameBatch> saveQueue;
    
    // Recording state
    private AtomicBoolean isRecording = new AtomicBoolean(false);
    private AtomicBoolean isPaused = new AtomicBoolean(false);
    private AtomicInteger frameCounter = new AtomicInteger(0);
    private AtomicInteger droppedFrames = new AtomicInteger(0);
    private AtomicLong recordingStartTime = new AtomicLong(0);
    private AtomicLong lastFrameTime = new AtomicLong(0);
    
    // Current session
    private RecordingSession currentSession;
    private String currentWorldName;
    
    // Performance tracking
    private volatile float currentFPS = 0;
    private final Queue<Long> frameTimings = new ConcurrentLinkedQueue<>();
    
    // Caches for optimization
    private final Map<Integer, EntitySnapshot> lastEntityState = new ConcurrentHashMap<>();
    private final Map<BlockPos, BlockState> lastBlockState = new ConcurrentHashMap<>();
    private PlayerSnapshot lastPlayerState;
    private WorldSnapshot lastWorldState;
    
    // Event listeners
    private final List<RecordingListener> listeners = new CopyOnWriteArrayList<>();
    
    // Configuration
    private RecordingConfig config = new RecordingConfig();
    
    public interface RecordingListener {
        void onRecordingStart();
        void onRecordingPause();
        void onRecordingResume();
        void onRecordingStop(RecordingSummary summary);
        void onFrameCaptured(int frameNumber);
        void onError(String error);
    }
    
    public static class RecordingConfig {
        public int targetFPS = 20;              // Target frames per second
        public int captureIntervalMs = 50;       // 1000 / targetFPS
        public int compressionLevel = 9;          // ZIP compression level (0-9)
        public int maxQueueSize = 1000;           // Max frames in queue
        public int batchSize = 100;                // Frames per save batch
        public boolean captureEntities = true;     // Capture entity movements
        public boolean captureBlocks = true;       // Capture block changes
        public boolean capturePlayer = true;       // Capture player data
        public boolean captureWorld = true;        // Capture world state
        public boolean useDeltaCompression = true; // Only store changes
        public boolean autoSave = true;            // Auto-save during recording
        public int autoSaveInterval = 500;         // Auto-save every N frames
    }
    
    public static class RecordingSummary {
        public final int totalFrames;
        public final long duration;
        public final int droppedFrames;
        public final float averageFPS;
        public final long dataSize;
        public final String worldName;
        
        public RecordingSummary(int frames, long duration, int dropped, float fps, long size, String world) {
            this.totalFrames = frames;
            this.duration = duration;
            this.droppedFrames = dropped;
            this.averageFPS = fps;
            this.dataSize = size;
            this.worldName = world;
        }
    }
    
    private ReplayRecorder() {
        this.client = MinecraftClient.getInstance();
        
        // Create thread pools with custom names
        this.captureExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Tcosmatic-Capture");
            t.setDaemon(true);
            t.setPriority(Thread.MAX_PRIORITY);
            return t;
        });
        
        this.compressionExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(), r -> {
                Thread t = new Thread(r, "Tcosmatic-Compression");
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY);
                return t;
            });
        
        this.saveExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Tcosmatic-Save");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
        
        this.monitorExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Tcosmatic-Monitor");
            t.setDaemon(true);
            return t;
        });
        
        // Initialize queues
        this.rawFrameQueue = new LinkedBlockingQueue<>(config.maxQueueSize);
        this.compressedFrameQueue = new LinkedBlockingQueue<>(config.maxQueueSize);
        this.saveQueue = new LinkedBlockingQueue<>(100);
        
        TcosmaticReplayMod.LOGGER.info("🎥 ReplayRecorder initialized with {} compression threads", 
            Runtime.getRuntime().availableProcessors());
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
            TcosmaticReplayMod.LOGGER.info("▶️ Starting recording in world: {}", worldName);
            
            // Reset state
            frameCounter.set(0);
            droppedFrames.set(0);
            recordingStartTime.set(System.currentTimeMillis());
            lastFrameTime.set(System.currentTimeMillis());
            currentWorldName = worldName;
            
            // Clear caches
            lastEntityState.clear();
            lastBlockState.clear();
            lastPlayerState = null;
            lastWorldState = null;
            frameTimings.clear();
            
            // Create new session
            currentSession = new RecordingSession(worldName);
            
            // Start all threads
            startCaptureThread();
            startCompressionThreads();
            startSaveThread();
            startMonitorThread();
            
            isRecording.set(true);
            isPaused.set(false);
            
            // Notify listeners
            notifyListeners(l -> l.onRecordingStart());
            
            TcosmaticReplayMod.LOGGER.info("✅ Recording started successfully");
            
        } catch (Exception e) {
            TcosmaticReplayMod.LOGGER.error("❌ Failed to start recording", e);
            notifyListeners(l -> l.onError("Failed to start: " + e.getMessage()));
        }
    }
    
    private void startCaptureThread() {
        captureExecutor.submit(() -> {
            TcosmaticReplayMod.LOGGER.info("Capture thread started");
            
            while (isRecording.get()) {
                try {
                    if (!isPaused.get() && client.world != null && client.player != null) {
                        long now = System.currentTimeMillis();
                        long timeSinceLastFrame = now - lastFrameTime.get();
                        
                        // Maintain target FPS
                        if (timeSinceLastFrame >= config.captureIntervalMs) {
                            captureFrame();
                            lastFrameTime.set(now);
                            
                            // Track timing for FPS calculation
                            frameTimings.offer(now);
                            while (frameTimings.size() > 100) {
                                frameTimings.poll();
                            }
                        }
                    }
                    
                    // Small sleep to prevent CPU hogging
                    Thread.sleep(1);
                    
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
    
    private void captureFrame() {
        try {
            int frameNum = frameCounter.incrementAndGet();
            long timestamp = System.currentTimeMillis();
            
            RawFrame frame = new RawFrame();
            frame.frameNumber = frameNum;
            frame.timestamp = timestamp;
            
            // Capture player data
            if (config.capturePlayer) {
                frame.player = capturePlayer();
            }
            
            // Capture entity data
            if (config.captureEntities) {
                frame.entities = captureEntities();
            }
            
            // Capture block changes
            if (config.captureBlocks) {
                frame.blockChanges = captureBlockChanges();
            }
            
            // Capture world state
            if (config.captureWorld) {
                frame.world = captureWorld();
            }
            
            // Add to queue for compression
            if (!rawFrameQueue.offer(frame, 100, TimeUnit.MILLISECONDS)) {
                droppedFrames.incrementAndGet();
                TcosmaticReplayMod.LOGGER.warn("Frame queue full, dropped frame {}", frameNum);
            }
            
            // Notify listeners
            notifyListeners(l -> l.onFrameCaptured(frameNum));
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            TcosmaticReplayMod.LOGGER.error("Failed to capture frame", e);
        }
    }
    
    private PlayerSnapshot capturePlayer() {
        if (client.player == null) return null;
        
        PlayerSnapshot snapshot = new PlayerSnapshot();
        ClientPlayerEntity player = client.player;
        
        snapshot.x = player.getX();
        snapshot.y = player.getY();
        snapshot.z = player.getZ();
        snapshot.yaw = player.getYaw();
        snapshot.pitch = player.getPitch();
        snapshot.headYaw = player.headYaw;
        snapshot.health = player.getHealth();
        snapshot.foodLevel = player.getHungerManager().getFoodLevel();
        snapshot.saturation = player.getHungerManager().getSaturationLevel();
        snapshot.sprinting = player.isSprinting();
        snapshot.sneaking = player.isSneaking();
        snapshot.swimming = player.isSwimming();
        snapshot.falling = player.getVelocity().y < -0.5;
        snapshot.velocityX = player.getVelocity().x;
        snapshot.velocityY = player.getVelocity().y;
        snapshot.velocityZ = player.getVelocity().z;
        
        // Check if using delta compression
        if (config.useDeltaCompression && lastPlayerState != null) {
            snapshot.delta = true;
            snapshot.dx = snapshot.x - lastPlayerState.x;
            snapshot.dy = snapshot.y - lastPlayerState.y;
            snapshot.dz = snapshot.z - lastPlayerState.z;
            snapshot.dyaw = snapshot.yaw - lastPlayerState.yaw;
            snapshot.dpitch = snapshot.pitch - lastPlayerState.pitch;
        }
        
        lastPlayerState = snapshot;
        return snapshot;
    }
    
    private List<EntitySnapshot> captureEntities() {
        if (client.world == null) return new ArrayList<>();
        
        List<EntitySnapshot> snapshots = new ArrayList<>();
        
        for (Entity entity : client.world.getEntities()) {
            // Skip players (handled separately)
            if (entity instanceof PlayerEntity) continue;
            
            int id = entity.getId();
            EntitySnapshot snapshot = new EntitySnapshot();
            snapshot.id = id;
            snapshot.type = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
            snapshot.x = entity.getX();
            snapshot.y = entity.getY();
            snapshot.z = entity.getZ();
            snapshot.yaw = entity.getYaw();
            snapshot.pitch = entity.getPitch();
            snapshot.velocityX = entity.getVelocity().x;
            snapshot.velocityY = entity.getVelocity().y;
            snapshot.velocityZ = entity.getVelocity().z;
            
            EntitySnapshot last = lastEntityState.get(id);
            
            // Check if entity changed significantly
            if (shouldCaptureEntity(snapshot, last)) {
                if (config.useDeltaCompression && last != null) {
                    snapshot.delta = true;
                    snapshot.dx = snapshot.x - last.x;
                    snapshot.dy = snapshot.y - last.y;
                    snapshot.dz = snapshot.z - last.z;
                    snapshot.dyaw = snapshot.yaw - last.yaw;
                    snapshot.dpitch = snapshot.pitch - last.pitch;
                }
                
                snapshots.add(snapshot);
                lastEntityState.put(id, snapshot);
            }
        }
        
        // Clean up dead entities
        lastEntityState.keySet().removeIf(id -> client.world.getEntityById(id) == null);
        
        return snapshots;
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
        
        // Check velocity change
        double dvx = current.velocityX - last.velocityX;
        double dvy = current.velocityY - last.velocityY;
        double dvz = current.velocityZ - last.velocityZ;
        if (dvx*dvx + dvy*dvy + dvz*dvz > 0.01) return true;
        
        return false;
    }
    
    private List<BlockChange> captureBlockChanges() {
        // This would need a block change listener system
        // For now, return empty list
        return new ArrayList<>();
    }
    
    private WorldSnapshot captureWorld() {
        if (client.world == null) return null;
        
        WorldSnapshot snapshot = new WorldSnapshot();
        snapshot.time = client.world.getTime();
        snapshot.dayTime = client.world.getTimeOfDay();
        snapshot.raining = client.world.isRaining();
        snapshot.thundering = client.world.isThundering();
        snapshot.rainGradient = client.world.getRainGradient(1.0f);
        snapshot.thunderGradient = client.world.getThunderGradient(1.0f);
        snapshot.moonPhase = client.world.getMoonPhase();
        snapshot.difficulty = client.world.getDifficulty().getId();
        
        return snapshot;
    }
    
    private void startCompressionThreads() {
        int threadCount = Runtime.getRuntime().availableProcessors();
        
        for (int i = 0; i < threadCount; i++) {
            compressionExecutor.submit(() -> {
                String threadName = Thread.currentThread().getName();
                TcosmaticReplayMod.LOGGER.debug("Compression thread {} started", threadName);
                
                while (isRecording.get() || !rawFrameQueue.isEmpty()) {
                    try {
                        RawFrame raw = rawFrameQueue.poll(100, TimeUnit.MILLISECONDS);
                        if (raw != null) {
                            ProcessedFrame processed = compressFrame(raw);
                            if (processed != null) {
                                compressedFrameQueue.offer(processed, 100, TimeUnit.MILLISECONDS);
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        TcosmaticReplayMod.LOGGER.error("Compression error", e);
                    }
                }
                
                TcosmaticReplayMod.LOGGER.debug("Compression thread {} stopped", threadName);
            });
        }
    }
    
    private ProcessedFrame compressFrame(RawFrame raw) {
        try {
            ProcessedFrame processed = new ProcessedFrame();
            processed.frameNumber = raw.frameNumber;
            processed.timestamp = raw.timestamp;
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            
            // Write frame header
            dos.writeInt(raw.frameNumber);
            dos.writeLong(raw.timestamp);
            
            // Write player data
            if (raw.player != null) {
                dos.writeBoolean(true);
                writePlayerData(dos, raw.player);
            } else {
                dos.writeBoolean(false);
            }
            
            // Write entity data
            dos.writeInt(raw.entities.size());
            for (EntitySnapshot entity : raw.entities) {
                writeEntityData(dos, entity);
            }
            
            // Write block changes
            dos.writeInt(raw.blockChanges.size());
            for (BlockChange change : raw.blockChanges) {
                writeBlockChange(dos, change);
            }
            
            // Write world data
            if (raw.world != null) {
                dos.writeBoolean(true);
                writeWorldData(dos, raw.world);
            } else {
                dos.writeBoolean(false);
            }
            
            dos.flush();
            byte[] data = baos.toByteArray();
            
            // Compress the data
            ByteArrayOutputStream compressedBAOS = new ByteArrayOutputStream();
            try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressedBAOS, 
                    new Deflater(config.compressionLevel))) {
                deflater.write(data);
            }
            
            processed.compressedData = compressedBAOS.toByteArray();
            processed.originalSize = data.length;
            processed.compressedSize = processed.compressedData.length;
            
            return processed;
            
        } catch (Exception e) {
            TcosmaticReplayMod.LOGGER.error("Failed to compress frame {}", raw.frameNumber, e);
            return null;
        }
    }
    
    private void writePlayerData(DataOutputStream dos, PlayerSnapshot player) throws IOException {
        if (player.delta && config.useDeltaCompression) {
            dos.writeBoolean(true); // delta
            dos.writeFloat(player.dx);
            dos.writeFloat(player.dy);
            dos.writeFloat(player.dz);
            dos.writeFloat(player.dyaw);
            dos.writeFloat(player.dpitch);
        } else {
            dos.writeBoolean(false); // full
            dos.writeFloat((float)player.x);
            dos.writeFloat((float)player.y);
            dos.writeFloat((float)player.z);
            dos.writeFloat(player.yaw);
            dos.writeFloat(player.pitch);
        }
        
        dos.writeFloat(player.headYaw);
        dos.writeFloat(player.health);
        dos.writeInt(player.foodLevel);
        dos.writeFloat(player.saturation);
        dos.writeBoolean(player.sprinting);
        dos.writeBoolean(player.sneaking);
        dos.writeBoolean(player.swimming);
        dos.writeBoolean(player.falling);
        dos.writeFloat((float)player.velocityX);
        dos.writeFloat((float)player.velocityY);
        dos.writeFloat((float)player.velocityZ);
    }
    
    private void writeEntityData(DataOutputStream dos, EntitySnapshot entity) throws IOException {
        dos.writeInt(entity.id);
        dos.writeUTF(entity.type);
        
        if (entity.delta && config.useDeltaCompression) {
            dos.writeBoolean(true); // delta
            dos.writeFloat(entity.dx);
            dos.writeFloat(entity.dy);
            dos.writeFloat(entity.dz);
            dos.writeFloat(entity.dyaw);
            dos.writeFloat(entity.dpitch);
        } else {
            dos.writeBoolean(false); // full
            dos.writeFloat((float)entity.x);
            dos.writeFloat((float)entity.y);
            dos.writeFloat((float)entity.z);
            dos.writeFloat(entity.yaw);
            dos.writeFloat(entity.pitch);
        }
        
        dos.writeFloat((float)entity.velocityX);
        dos.writeFloat((float)entity.velocityY);
        dos.writeFloat((float)entity.velocityZ);
    }
    
    private void writeBlockChange(DataOutputStream dos, BlockChange change) throws IOException {
        dos.writeInt(change.x);
        dos.writeInt(change.y);
        dos.writeInt(change.z);
        dos.writeInt(change.oldState);
        dos.writeInt(change.newState);
    }
    
    private void writeWorldData(DataOutputStream dos, WorldSnapshot world) throws IOException {
        dos.writeLong(world.time);
        dos.writeLong(world.dayTime);
        dos.writeBoolean(world.raining);
        dos.writeBoolean(world.thundering);
        dos.writeFloat(world.rainGradient);
        dos.writeFloat(world.thunderGradient);
        dos.writeInt(world.moonPhase);
        dos.writeInt(world.difficulty);
    }
    
    private void startSaveThread() {
        saveExecutor.submit(() -> {
            TcosmaticReplayMod.LOGGER.info("Save thread started");
            
            List<ProcessedFrame> batch = new ArrayList<>();
            int lastSavedFrame = 0;
            
            while (isRecording.get() || !compressedFrameQueue.isEmpty()) {
                try {
                    ProcessedFrame frame = compressedFrameQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (frame != null) {
                        batch.add(frame);
                        
                        // Save in batches
                        if (batch.size() >= config.batchSize || 
                            (config.autoSave && frame.frameNumber - lastSavedFrame >= config.autoSaveInterval)) {
                            
                            saveBatch(new ArrayList<>(batch));
                            lastSavedFrame = frame.frameNumber;
                            batch.clear();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    TcosmaticReplayMod.LOGGER.error("Save error", e);
                }
            }
            
            // Save remaining frames
            if (!batch.isEmpty()) {
                saveBatch(batch);
            }
            
            TcosmaticReplayMod.LOGGER.info("Save thread stopped");
        });
    }
    
    private void saveBatch(List<ProcessedFrame> batch) {
        try {
            currentSession.addFrames(batch);
            TcosmaticReplayMod.LOGGER.debug("Saved batch of {} frames", batch.size());
        } catch (Exception e) {
            TcosmaticReplayMod.LOGGER.error("Failed to save batch", e);
        }
    }
    
    private void startMonitorThread() {
        monitorExecutor.scheduleAtFixedRate(() -> {
            if (isRecording.get()) {
                // Calculate current FPS
                long now = System.currentTimeMillis();
                long oneSecondAgo = now - 1000;
                
                int framesLastSecond = 0;
                Iterator<Long> it = frameTimings.iterator();
                while (it.hasNext()) {
                    if (it.next() >= oneSecondAgo) {
                        framesLastSecond++;
                    }
                }
                
                currentFPS = framesLastSecond;
                
                // Log stats every 5 seconds
                if (frameCounter.get() % (config.targetFPS * 5) == 0) {
                    TcosmaticReplayMod.LOGGER.info(
                        "Recording stats - Frame: {}, FPS: {:.1f}, Dropped: {}, Queues: {}/{}/{}",
                        frameCounter.get(), currentFPS, droppedFrames.get(),
                        rawFrameQueue.size(), compressedFrameQueue.size(), saveQueue.size()
                    );
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
    }
    
    public void pauseRecording() {
        if (isRecording.get() && !isPaused.get()) {
            isPaused.set(true);
            TcosmaticReplayMod.LOGGER.info("⏸️ Recording paused at frame {}", frameCounter.get());
            notifyListeners(l -> l.onRecordingPause());
        }
    }
    
    public void resumeRecording() {
        if (isRecording.get() && isPaused.get()) {
            isPaused.set(false);
            lastFrameTime.set(System.currentTimeMillis()); // Reset timing
            TcosmaticReplayMod.LOGGER.info("▶️ Recording resumed");
            notifyListeners(l -> l.onRecordingResume());
        }
    }
    
    public RecordingSummary stopRecording() {
        if (!isRecording.get()) return null;
        
        TcosmaticReplayMod.LOGGER.info("⏹️ Stopping recording...");
        isRecording.set(false);
        
        try {
            // Wait for queues to empty (max 10 seconds)
            int timeout = 0;
            while ((!rawFrameQueue.isEmpty() || !compressedFrameQueue.isEmpty()) && timeout < 100) {
                Thread.sleep(100);
                timeout++;
            }
            
            // Shutdown executors
            captureExecutor.shutdown();
            compressionExecutor.shutdown();
            saveExecutor.shutdown();
            monitorExecutor.shutdown();
            
            // Wait for termination
            captureExecutor.awaitTermination(5, TimeUnit.SECONDS);
            compressionExecutor.awaitTermination(5, TimeUnit.SECONDS);
            saveExecutor.awaitTermination(5, TimeUnit.SECONDS);
            monitorExecutor.awaitTermination(5, TimeUnit.SECONDS);
            
            // Finalize session
            if (currentSession != null) {
                currentSession.finalizeAndSave();
                
                // Calculate stats
                long duration = System.currentTimeMillis() - recordingStartTime.get();
                float avgFPS = (frameCounter.get() * 1000.0f) / duration;
                long dataSize = currentSession.getTotalDataSize();
                
                RecordingSummary summary = new RecordingSummary(
                    frameCounter.get(), duration, droppedFrames.get(),
                    avgFPS, dataSize, currentWorldName
                );
                
                // Notify listeners
                notifyListeners(l -> l.onRecordingStop(summary));
                
                TcosmaticReplayMod.LOGGER.info(
                    "✅ Recording complete: {} frames, {:.2f} seconds, {:.1f} FPS, {:.2f} MB",
                    frameCounter.get(), duration / 1000.0, avgFPS,
                    dataSize / (1024.0 * 1024.0)
                );
                
                return summary;
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            TcosmaticReplayMod.LOGGER.error("Stop recording interrupted", e);
        }
        
        return null;
    }
    
    public void addListener(RecordingListener listener) {
        listeners.add(listener);
    }
    
    public void removeListener(RecordingListener listener) {
        listeners.remove(listener);
    }
    
    private void notifyListeners(java.util.function.Consumer<RecordingListener> action) {
        for (RecordingListener listener : listeners) {
            try {
                action.accept(listener);
            } catch (Exception e) {
                TcosmaticReplayMod.LOGGER.error("Listener notification failed", e);
            }
        }
    }
    
    public void updateConfig(RecordingConfig newConfig) {
        this.config = newConfig;
        TcosmaticReplayMod.LOGGER.info("Recording config updated");
    }
    
    // Getters
    public boolean isRecording() { return isRecording.get(); }
    public boolean isPaused() { return isPaused.get(); }
    public int getFrameCount() { return frameCounter.get(); }
    public int getDroppedFrames() { return droppedFrames.get(); }
    public float getCurrentFPS() { return currentFPS; }
    public long getStartTime() { return recordingStartTime.get(); }
    public String getCurrentWorld() { return currentWorldName; }
    public RecordingSession getCurrentSession() { return currentSession; }
    
    public int getFrameQueueSize() { return rawFrameQueue.size(); }
    public int getCompressionQueueSize() { return compressedFrameQueue.size(); }
    public int getSaveQueueSize() { return saveQueue.size(); }
    
    public RecordingConfig getConfig() { return config; }
    
    // Inner classes for data structures
    public static class RawFrame {
        public int frameNumber;
        public long timestamp;
        public PlayerSnapshot player;
        public List<EntitySnapshot> entities = new ArrayList<>();
        public List<BlockChange> blockChanges = new ArrayList<>();
        public WorldSnapshot world;
    }
    
    public static class ProcessedFrame {
        public int frameNumber;
        public long timestamp;
        public byte[] compressedData;
        public int originalSize;
        public int compressedSize;
    }
    
    public static class PlayerSnapshot {
        public double x, y, z;
        public float yaw, pitch, headYaw;
        public float health;
        public int foodLevel;
        public float saturation;
        public boolean sprinting, sneaking, swimming, falling;
        public double velocityX, velocityY, velocityZ;
        
        // Delta compression fields
        public boolean delta = false;
        public float dx, dy, dz;
        public float dyaw, dpitch;
    }
    
    public static class EntitySnapshot {
        public int id;
        public String type;
        public double x, y, z;
        public float yaw, pitch;
        public double velocityX, velocityY, velocityZ;
        
        // Delta compression fields
        public boolean delta = false;
        public float dx, dy, dz;
        public float dyaw, dpitch;
    }
    
    public static class BlockChange {
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
    
    public static class WorldSnapshot {
        public long time;
        public long dayTime;
        public boolean raining;
        public boolean thundering;
        public float rainGradient;
        public float thunderGradient;
        public int moonPhase;
        public int difficulty;
    }
}
