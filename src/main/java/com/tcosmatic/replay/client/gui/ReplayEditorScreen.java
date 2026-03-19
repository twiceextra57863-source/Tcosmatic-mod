package com.tcosmatic.replay.client.gui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import com.tcosmatic.replay.client.playback.ReplayPlayer;
import com.tcosmatic.replay.client.playback.KeyframeSystem;
import com.tcosmatic.replay.client.playback.CameraPath;
import com.tcosmatic.replay.client.utils.ScrollHandler;
import com.tcosmatic.replay.common.ReplayMetadata;

public class ReplayEditorScreen extends Screen {
    private final Screen parent;
    private final ReplayMetadata replay;
    private final ReplayPlayer player;
    private final KeyframeSystem keyframeSystem;
    private final CameraPath cameraPath;
    private final ScrollHandler scrollHandler;
    
    private int timelineX = 50;
    private int timelineY = 100;
    private int timelineWidth;
    private int timelineHeight = 50;
    
    private boolean isDraggingTimeline = false;
    private boolean isAddingKeyframes = false;
    private int selectedKeyframeIndex = -1;
    
    public ReplayEditorScreen(Screen parent, ReplayMetadata replay) {
        super(Text.literal("Editing: " + replay.getName()));
        this.parent = parent;
        this.replay = replay;
        this.player = new ReplayPlayer();
        this.keyframeSystem = player.getKeyframeSystem();
        this.cameraPath = player.getCameraPath();
        this.scrollHandler = ScrollHandler.getInstance();
    }
    
    @Override
    protected void init() {
        timelineWidth = this.width - 100;
        
        // Load replay
        player.loadReplay(replay.getId()).thenAccept(success -> {
            if (success) {
                player.play();
            }
        });
        
        // Add buttons
        this.addDrawableChild(new TcosmaticButton(
            10, 10, 60, 20,
            Text.literal("← Back"), button -> {
                player.stop();
                client.setScreen(parent);
            }, 4
        ));
        
        this.addDrawableChild(new TcosmaticButton(
            80, 10, 60, 20,
            Text.literal("⏯️ Play/Pause"), button -> {
                if (player.getState() == Replayer.PlaybackState.PLAYING) {
                    player.pause();
                } else {
                    player.play();
                }
            }, 0
        ));
        
        this.addDrawableChild(new TcosmaticButton(
            150, 10, 60, 20,
            Text.literal("➕ Keyframe"), button -> {
                player.addKeyframe();
            }, 1
        ));
        
        this.addDrawableChild(new TcosmaticButton(
            220, 10, 80, 20,
            Text.literal("🎥 Camera Path"), button -> {
                cameraPath.activate();
            }, 4
        ));
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        
        // Title
        context.drawCenteredTextWithShadow(this.textRenderer, 
            "✂️ Tcosmatic Replay Editor: " + replay.getName(), 
            this.width / 2, 30, 0xFFAA00);
        
        // Render timeline
        renderTimeline(context, mouseX, mouseY);
        
        // Render keyframes
        renderKeyframes(context);
        
        // Render playback info
        renderPlaybackInfo(context);
        
        // Update playback
        player.updatePlayback();
        
        super.render(context, mouseX, mouseY, delta);
    }
    
    private void renderTimeline(DrawContext context, int mouseX, int mouseY) {
        // Timeline background
        context.fill(timelineX, timelineY, 
                    timelineX + timelineWidth, timelineY + timelineHeight, 
                    0xFF333333);
        
        // Playback position
        float progress = player.getPlaybackProgress();
        int posX = timelineX + (int)(timelineWidth * progress);
        context.fill(posX - 2, timelineY - 5, 
                    posX + 2, timelineY + timelineHeight + 5, 
                    0xFFFFAA00);
        
        // Time markers
        long totalSeconds = player.getTotalFrames() / 20;
        for (int i = 0; i <= 10; i++) {
            int x = timelineX + (i * timelineWidth / 10);
            context.drawVerticalLine(x, timelineY - 5, timelineY, 0xFF666666);
            
            String timeLabel = String.format("%d:%02d", i * totalSeconds / 60, (i * totalSeconds) % 60);
            context.drawText(this.textRenderer, timeLabel, 
                           x - 15, timelineY - 20, 0xFFFFFF, true);
        }
        
        // Handle timeline dragging
        if (isDraggingTimeline) {
            int newX = Math.max(timelineX, Math.min(timelineX + timelineWidth, mouseX));
            float newProgress = (float)(newX - timelineX) / timelineWidth;
            player.seekToFrame((long)(newProgress * player.getTotalFrames()));
        }
    }
    
    private void renderKeyframes(DrawContext context) {
        int y = timelineY + timelineHeight + 10;
        
        context.drawText(this.textRenderer, "Keyframes:", 
                        timelineX, y, 0xFFFFFF, true);
        
        List<KeyframeSystem.Keyframe> keyframes = keyframeSystem.getKeyframes();
        for (int i = 0; i < keyframes.size(); i++) {
            KeyframeSystem.Keyframe kf = keyframes.get(i);
            float progress = (float)kf.frame / player.getTotalFrames();
            int x = timelineX + (int)(timelineWidth * progress);
            
            // Keyframe marker
            boolean isSelected = (i == selectedKeyframeIndex);
            int color = isSelected ? 0xFFFFAA00 : 0xFF00AAFF;
            context.fill(x - 3, y + 20, x + 3, y + 26, color);
            
            // Keyframe info
            if (isSelected) {
                String info = String.format("Frame %d | Pos: %.1f, %.1f, %.1f", 
                    kf.frame, kf.x, kf.y, kf.z);
                context.drawText(this.textRenderer, info, 
                               x, y + 30, 0xFFFFFF, true);
            }
        }
    }
    
    private void renderPlaybackInfo(DrawContext context) {
        String status = "Status: ";
        switch(player.getState()) {
            case PLAYING: status += "▶️ Playing"; break;
            case PAUSED: status += "⏸️ Paused"; break;
            case STOPPED: status += "⏹️ Stopped"; break;
            default: status += "⚫ Idle";
        }
        
        context.drawText(this.textRenderer, status, 
                        10, this.height - 30, 0xFFFFFF, true);
        
        String frameInfo = String.format("Frame: %d/%d (%.1f%%)", 
            player.getCurrentFrame(), player.getTotalFrames(),
            player.getPlaybackProgress() * 100);
        
        context.drawText(this.textRenderer, frameInfo, 
                        10, this.height - 20, 0xFFFFFF, true);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Timeline click
        if (mouseX >= timelineX && mouseX <= timelineX + timelineWidth &&
            mouseY >= timelineY && mouseY <= timelineY + timelineHeight) {
            isDraggingTimeline = true;
            return true;
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDraggingTimeline = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Handle scroll keys
        if (scrollHandler.handleKeyPress(keyCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
                         }
