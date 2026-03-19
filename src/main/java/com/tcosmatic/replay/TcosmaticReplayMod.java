package com.tcosmatic.replay;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.tcosmatic.replay.client.ReplayClient;
import com.tcosmatic.replay.client.gui.DashboardScreen;
import com.tcosmatic.replay.client.utils.FileManager;
import com.tcosmatic.replay.client.utils.ScrollHandler;

public class TcosmaticReplayMod implements ModInitializer {
    public static final String MOD_ID = "tcosmatic";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static TcosmaticReplayMod instance;
    
    // Keybindings
    private static KeyBinding openDashboardKey;
    private static KeyBinding openReplaysKey;
    private static KeyBinding scrollUpKey;
    private static KeyBinding scrollDownKey;
    private static KeyBinding quickRecordKey;
    
    // State
    private boolean isModInitialized = false;
    private long modStartTime;
    
    public static TcosmaticReplayMod getInstance() {
        return instance;
    }
    
    @Override
    public void onInitialize() {
        instance = this;
        modStartTime = System.currentTimeMillis();
        
        LOGGER.info("☠️ TCOSMATIC REPLAY MOD - INITIALIZING... ☠️");
        LOGGER.info("🔥 Ultimate Replay Mod by Tcosmatic 🔥");
        
        try {
            // Initialize core systems
            initializeCoreSystems();
            
            // Register keybindings
            registerKeyBindings();
            
            // Register events
            registerEvents();
            
            // Create mod directory
            FileManager.getInstance(); // This creates directories
            
            isModInitialized = true;
            LOGGER.info("✅ Tcosmatic Replay Mod loaded successfully in {}ms!", 
                       System.currentTimeMillis() - modStartTime);
            
        } catch (Exception e) {
            LOGGER.error("💀 FATAL ERROR - Tcosmatic Mod Failed to Load! 💀", e);
            e.printStackTrace();
        }
    }
    
    private void initializeCoreSystems() {
        // Pre-warm the systems
        ReplayClient.getInstance(); // Initialize client
        ScrollHandler.getInstance(); // Initialize scroll handler
    }
    
    private void registerKeyBindings() {
        LOGGER.info("Registering Tcosmatic keybindings...");
        
        openDashboardKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.tcosmatic.open_dashboard",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "category.tcosmatic.replay"
        ));
        
        openReplaysKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.tcosmatic.open_replays",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
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
        
        quickRecordKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.tcosmatic.quick_record",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F6,
            "category.tcosmatic.replay"
        ));
        
        LOGGER.info("✅ Keybindings registered!");
    }
    
    private void registerEvents() {
        LOGGER.info("Registering Tcosmatic events...");
        
        // Client tick event for key handling
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.currentScreen != null) return;
            
            ReplayClient replayClient = ReplayClient.getInstance();
            
            while (openDashboardKey.wasPressed()) {
                LOGGER.info("Opening Tcosmatic Dashboard");
                client.setScreen(new DashboardScreen(null));
            }
            
            while (openReplaysKey.wasPressed()) {
                LOGGER.info("Opening Replay Browser");
                client.setScreen(new com.tcosmatic.replay.client.gui.ReplayBrowserScreen(null));
            }
            
            while (quickRecordKey.wasPressed()) {
                LOGGER.info("Quick Record Toggle");
                if (replayClient.isRecording()) {
                    replayClient.stopRecording();
                } else {
                    replayClient.startRecording();
                }
            }
            
            // Handle scrolling
            replayClient.handleScrolling(
                scrollUpKey.isPressed(),
                scrollDownKey.isPressed()
            );
        });
        
        // World leave event - auto save
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            LOGGER.info("Player disconnected - Auto-saving replay if recording");
            ReplayClient.getInstance().autoSaveIfRecording();
        });
        
        LOGGER.info("✅ Events registered!");
    }
    
    public static KeyBinding getScrollUpKey() {
        return scrollUpKey;
    }
    
    public static KeyBinding getScrollDownKey() {
        return scrollDownKey;
    }
}
