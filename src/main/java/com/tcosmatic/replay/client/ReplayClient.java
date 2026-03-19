package com.tcosmatic.replay.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import com.tcosmatic.replay.TcosmaticReplayMod;
import com.tcosmatic.replay.client.gui.*;
import com.tcosmatic.replay.client.recording.ReplayRecorder;
import com.tcosmatic.replay.client.playback.ReplayPlayer;
import com.tcosmatic.replay.client.utils.FileManager;
import com.tcosmatic.replay.client.utils.ScrollHandler;

import java.util.*;
import java.util.concurrent.*;

public class ReplayClient implements ClientModInitializer {
    private static ReplayClient instance;
    private final MinecraftClient client;
    
    // Core systems
    private ReplayRecorder recorder;
    private ReplayPlayer player;
    private FileManager fileManager;
    private ScrollHandler scrollHandler;
    
    // State management
    private ClientState currentState = ClientState.IDLE;
    private final Map<String, Object> sessionData;
    private final Queue<Runnable> taskQueue;
    
    // Performance monitoring
    private final PerformanceMonitor performanceMonitor;
    private boolean isInitialized = false;
    
    // Keybindings
    private KeyBinding openDashboardKey;
    private KeyBinding quickRecordKey;
    private KeyBinding pauseRecordingKey;
    private KeyBinding addKeyframeKey;
    private KeyBinding scrollUpKey;
    private KeyBinding scrollDownKey;
    
    // UI State
    private boolean showHUD = true;
    private int hudPosition = 1; // 1=top-right, 2=top-left, 3=bottom-right, 4=bottom-left
    private long lastNotificationTime = 0;
    private String currentNotification = "";
    
    public enum ClientState {
        IDLE,
        RECORDING,
        RECORDING_PAUSED,
        PLAYBACK,
        PLAYBACK_PAUSED,
        EXPORTING,
        RENDERING
    }
    
    public ReplayClient() {
        this.client = MinecraftClient.getInstance();
        this.sessionData = new ConcurrentHashMap<>();
        this.taskQueue = new ConcurrentLinkedQueue<>();
        this.performanceMonitor = new PerformanceMonitor();
    }
    
    public static ReplayClient getInstance() {
        if (instance == null) {
            instance = new ReplayClient();
        }
        return instance;
    }
    
    @Override
    public void onInitializeClient() {
        TcosmaticReplayMod.LOGGER.info("🎮 Initializing Tcosmatic Replay Client...");
        
        try {
            // Initialize core systems
            initializeCoreSystems();
            
            // Register keybindings
            registerKeyBindings();
            
            // Register events
            registerEvents();
            
            // Register HUD
            registerHUD();
            
            // Start performance monitor
            performanceMonitor.start();
            
            isInitialized = true;
            
            TcosmaticReplayMod.LOGGER.info("✅ Tcosmatic Replay Client initialized successfully!");
            showNotification("Tcosmatic Replay Mod Ready! Press R to open dashboard");
            
        } catch (Exception e) {
            TcosmaticReplayMod.LOGGER.error("💀 Failed to initialize ReplayClient", e);
            e.printStackTrace();
        }
    }
    
    private void initializeCoreSystems() {
        TcosmaticReplayMod.LOGGER.info("Initializing core systems...");
        
        // Initialize file manager first (creates directories)
        this.fileManager = FileManager.getInstance();
        
        // Initialize recorder and player
        this.recorder = ReplayRecorder.getInstance();
        this.player = new ReplayPlayer();
        
        // Initialize scroll handler
        this.scrollHandler = ScrollHandler.getInstance();
        
        // Pre-warm thread pools
        warmupThreadPools();
        
        TcosmaticReplayMod.LOGGER.info("Core systems initialized");
    }
    
    private void warmupThreadPools() {
        // Pre-create threads to avoid lag during recording
        Executors.newSingleThreadExecutor().submit(() -> {
            Thread.currentThread().setName("Tcosmatic-Warmup");
            TcosmaticReplayMod.LOGGER.debug("Thread pools warmed up");
            return null;
        });
    }
    
    private void registerKeyBindings() {
        TcosmaticReplayMod.LOGGER.info("Registering client keybindings...");
        
        openDashboardKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.tcosmatic.open_dashboard",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "category.tcosmatic.replay"
        ));
        
        quickRecordKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.tcosmatic.quick_record",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F6,
            "category.tcosmatic.replay"
        ));
        
        pauseRecordingKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.tcosmatic.pause_recording",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F7,
            "category.tcosmatic.replay"
        ));
        
        addKeyframeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.tcosmatic.add_keyframe",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            "category.tcosmatic.replay"
        ));
        
        scrollUpKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.tcosmatic.scroll_up",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_PAGE_UP,
            "category.tcosmatic.replay"
        ));
        
        scrollDownKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.tcosmatic.scroll_down",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_PAGE_DOWN,
            "category.tcosmatic.replay"
        ));
        
        TcosmaticReplayMod.LOGGER.info("✅ {} keybindings registered", 6);
    }
    
    private void registerEvents() {
        TcosmaticReplayMod.LOGGER.info("Registering client events...");
        
        // Client tick event for key handling and updates
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        
        // World join event
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            onWorldJoin();
        });
        
        // World leave event
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            onWorldLeave();
        });
    }
    
    private void registerHUD() {
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (showHUD && client.player != null) {
                renderHUD(drawContext);
            }
        });
    }
    
    private void onClientTick(MinecraftClient client) {
        if (client.player == null) return;
        
        // Process queued tasks
        processTaskQueue();
        
        // Handle key presses
        handleKeyInputs(client);
        
        // Update recorder/player states
        updateStates();
        
        // Update performance metrics
        performanceMonitor.tick();
    }
    
    private void handleKeyInputs(MinecraftClient client) {
        // Don't handle keys if a screen is open
        if (client.currentScreen != null) return;
        
        while (openDashboardKey.wasPressed()) {
            client.setScreen(new DashboardScreen(null));
        }
        
        while (quickRecordKey.wasPressed()) {
            toggleQuickRecord();
        }
        
        while (pauseRecordingKey.wasPressed()) {
            togglePauseRecording();
        }
        
        while (addKeyframeKey.wasPressed()) {
            addKeyframe();
        }
        
        // Handle scrolling
        handleScrolling(scrollUpKey.isPressed(), scrollDownKey.isPressed());
    }
    
    private void toggleQuickRecord() {
        if (currentState == ClientState.RECORDING) {
            stopRecording();
            showNotification("⏹️ Recording stopped");
        } else if (currentState == ClientState.RECORDING_PAUSED) {
            resumeRecording();
            showNotification("▶️ Recording resumed");
        } else {
            startRecording();
            showNotification("⏺️ Recording started");
        }
    }
    
    private void togglePauseRecording() {
        if (currentState == ClientState.RECORDING) {
            pauseRecording();
            showNotification("⏸️ Recording paused");
        } else if (currentState == ClientState.RECORDING_PAUSED) {
            resumeRecording();
            showNotification("▶️ Recording resumed");
        }
    }
    
    private void updateStates() {
        // Update recorder state
        if (recorder.isRecording()) {
            currentState = recorder.isPaused() ? 
                ClientState.RECORDING_PAUSED : ClientState.RECORDING;
        } else {
            currentState = ClientState.IDLE;
        }
        
        // Update player state if needed
        if (player.getState() != ReplayPlayer.PlaybackState.STOPPED) {
            currentState = ClientState.PLAYBACK;
        }
    }
    
    private void processTaskQueue() {
        Runnable task;
        while ((task = taskQueue.poll()) != null) {
            try {
                task.run();
            } catch (Exception e) {
                TcosmaticReplayMod.LOGGER.error("Task execution failed", e);
            }
        }
    }
    
    private void onWorldJoin() {
        TcosmaticReplayMod.LOGGER.info("Joined world: {}", 
            client.world != null ? client.world.getRegistryKey().getValue() : "unknown");
        
        // Auto-load last session if exists
        if (sessionData.containsKey("pendingRecording")) {
            showNotification("Previous recording session found. Press R to recover");
        }
    }
    
    private void onWorldLeave() {
        TcosmaticReplayMod.LOGGER.info("Leaving world, auto-saving if recording...");
        
        if (currentState == ClientState.RECORDING || 
            currentState == ClientState.RECORDING_PAUSED) {
            
            // Auto-save recording
            taskQueue.offer(() -> {
                recorder.stopRecording();
                showNotification("✅ Recording auto-saved");
            });
        }
    }
    
    private void renderHUD(DrawContext context) {
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        
        // Recording indicator
        if (currentState == ClientState.RECORDING) {
            renderRecordingIndicator(context, width, height);
        }
        
        // Performance stats (only in debug mode)
        if (client.options.debugEnabled) {
            renderPerformanceStats(context, width, height);
        }
        
        // Notifications
        renderNotification(context, width, height);
    }
    
    private void renderRecordingIndicator(DrawContext context, int width, int height) {
        int x = width - 120;
        int y = 10;
        
        // Background
        context.fill(x, y, x + 110, y + 40, 0xAA000000);
        context.drawBorder(x, y, 110, 40, 0xFFFF0000);
        
        // Red dot animation
        long time = System.currentTimeMillis() % 1000;
        int alpha = time < 500 ? 255 : (int)(255 * (1 - (time - 500) / 500.0));
        int color = (alpha << 24) | 0xFF0000;
        context.fill(x + 10, y + 10, x + 20, y + 20, color);
        
        // Text
        context.drawText(client.textRenderer, "🔴 REC", x + 30, y + 12, 0xFFFFFFFF, true);
        
        // Timer
        long duration = System.currentTimeMillis() - recorder.getStartTime();
        long seconds = duration / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        String timeStr = String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60);
        context.drawText(client.textRenderer, timeStr, x + 30, y + 25, 0xFFFFFFAA, true);
    }
    
    private void renderPerformanceStats(DrawContext context, int width, int height) {
        int x = 10;
        int y = height - 80;
        
        context.fill(x, y, x + 200, y + 70, 0xAA000000);
        
        // FPS
        String fpsStr = String.format("Record FPS: %.1f", performanceMonitor.getRecordingFPS());
        context.drawText(client.textRenderer, fpsStr, x + 10, y + 10, 0xFFFFFF, true);
        
        // Dropped frames
        String dropStr = String.format("Dropped: %d", recorder.getDroppedFrames());
        context.drawText(client.textRenderer, dropStr, x + 10, y + 25, 0xFFFFFF, true);
        
        // Queue sizes
        String queueStr = String.format("Queues: %d/%d/%d", 
            performanceMonitor.getFrameQueueSize(),
            performanceMonitor.getCompressionQueueSize(),
            performanceMonitor.getSaveQueueSize());
        context.drawText(client.textRenderer, queueStr, x + 10, y + 40, 0xFFFFFF, true);
        
        // Memory
        Runtime runtime = Runtime.getRuntime();
        long usedMB = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        String memStr = String.format("Memory: %d MB", usedMB);
        context.drawText(client.textRenderer, memStr, x + 10, y + 55, 0xFFFFFF, true);
    }
    
    private void renderNotification(DrawContext context, int width, int height) {
        if (currentNotification.isEmpty()) return;
        
        long timeSince = System.currentTimeMillis() - lastNotificationTime;
        if (timeSince > 3000) {
            currentNotification = "";
            return;
        }
        
        int alpha = timeSince > 2500 ? (int)(255 * (1 - (timeSince - 2500) / 500.0)) : 255;
        int color = (alpha << 24) | 0xFFFFFF;
        
        int x = width / 2 - 100;
        int y = height - 60;
        
        context.fill(x, y, x + 200, y + 30, (alpha / 2) << 24);
        context.drawCenteredTextWithShadow(client.textRenderer, 
            currentNotification, width / 2, y + 8, color);
    }
    
    // Public API methods
    
    public void startRecording() {
        if (currentState == ClientState.IDLE && client.world != null) {
            String worldName = client.world.getRegistryKey().getValue().toString();
            recorder.startRecording(worldName);
            currentState = ClientState.RECORDING;
            showNotification("⏺️ Recording started");
        }
    }
    
    public void stopRecording() {
        if (currentState == ClientState.RECORDING || 
            currentState == ClientState.RECORDING_PAUSED) {
            
            taskQueue.offer(() -> {
                recorder.stopRecording();
                currentState = ClientState.IDLE;
            });
        }
    }
    
    public void pauseRecording() {
        if (currentState == ClientState.RECORDING) {
            recorder.pauseRecording();
            currentState = ClientState.RECORDING_PAUSED;
        }
    }
    
    public void resumeRecording() {
        if (currentState == ClientState.RECORDING_PAUSED) {
            recorder.resumeRecording();
            currentState = ClientState.RECORDING;
        }
    }
    
    public void addKeyframe() {
        if (currentState == ClientState.PLAYBACK) {
            player.addKeyframe();
            showNotification("➕ Keyframe added");
        }
    }
    
    public void handleScrolling(boolean scrollUp, boolean scrollDown) {
        if (scrollUp) {
            scrollHandler.scrollUp();
        }
        if (scrollDown) {
            scrollHandler.scrollDown();
        }
    }
    
    public void showNotification(String message) {
        this.currentNotification = message;
        this.lastNotificationTime = System.currentTimeMillis();
        TcosmaticReplayMod.LOGGER.info("[NOTIFICATION] {}", message);
    }
    
    public void toggleHUD() {
        showHUD = !showHUD;
    }
    
    public void cycleHUDPosition() {
        hudPosition = (hudPosition % 4) + 1;
    }
    
    public void autoSaveIfRecording() {
        if (currentState == ClientState.RECORDING || 
            currentState == ClientState.RECORDING_PAUSED) {
            stopRecording();
        }
    }
    
    // Getters
    
    public ClientState getCurrentState() { return currentState; }
    public boolean isRecording() { return currentState == ClientState.RECORDING; }
    public boolean isPlaying() { return currentState == ClientState.PLAYBACK; }
    public ReplayRecorder getRecorder() { return recorder; }
    public ReplayPlayer getPlayer() { return player; }
    public FileManager getFileManager() { return fileManager; }
    public ScrollHandler getScrollHandler() { return scrollHandler; }
    public PerformanceMonitor getPerformanceMonitor() { return performanceMonitor; }
    
    public Map<String, Object> getSessionData() { return sessionData; }
    
    public void putSessionData(String key, Object value) {
        sessionData.put(key, value);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getSessionData(String key, T defaultValue) {
        return (T) sessionData.getOrDefault(key, defaultValue);
    }
    
    // Performance Monitor Inner Class
    public static class PerformanceMonitor {
        private final ScheduledExecutorService scheduler;
        private float recordingFPS = 0;
        private int framesThisSecond = 0;
        private long lastSecond = 0;
        private boolean isRunning = false;
        
        public PerformanceMonitor() {
            this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Tcosmatic-PerfMon");
                t.setDaemon(true);
                return t;
            });
        }
        
        public void start() {
            if (isRunning) return;
            isRunning = true;
            
            scheduler.scheduleAtFixedRate(() -> {
                recordingFPS = framesThisSecond;
                framesThisSecond = 0;
            }, 1, 1, TimeUnit.SECONDS);
        }
        
        public void tick() {
            long now = System.currentTimeMillis();
            if (now - lastSecond >= 1000) {
                lastSecond = now;
                framesThisSecond = 0;
            }
            framesThisSecond++;
        }
        
        public float getRecordingFPS() { return recordingFPS; }
        
        public int getFrameQueueSize() {
            return ReplayRecorder.getInstance().getFrameQueueSize();
        }
        
        public int getCompressionQueueSize() {
            return ReplayRecorder.getInstance().getCompressionQueueSize();
        }
        
        public int getSaveQueueSize() {
            return ReplayRecorder.getInstance().getSaveQueueSize();
        }
    }
          }
