package com.tcosmatic.replay.client.gui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import com.tcosmatic.replay.client.ReplayClient;
import com.tcosmatic.replay.client.utils.ScrollHandler;
import net.minecraft.client.gui.widget.ButtonWidget;

public class DashboardScreen extends Screen {
    private final Screen parent;
    private ScrollHandler scrollHandler = new ScrollHandler();
    private boolean isRecording = false;
    private boolean isPaused = false;
    
    public DashboardScreen(Screen parent) {
        super(Text.literal("Tcosmatic Dashboard"));
        this.parent = parent;
    }
    
    @Override
    protected void init() {
        int centerX = this.width / 2;
        
        // Start/Pause/Stop buttons
        if (!isRecording) {
            // Start Recording Button
            this.addDrawableChild(new TcosmaticButton(
                centerX - 60, this.height / 2 - 30, 50, 50,
                Text.literal(""), button -> startRecording(), 1
            ));
        } else {
            if (!isPaused) {
                // Pause Button
                this.addDrawableChild(new TcosmaticButton(
                    centerX - 60, this.height / 2 - 30, 50, 50,
                    Text.literal(""), button -> pauseRecording(), 2
                ));
            } else {
                // Resume Button (modified record icon)
                this.addDrawableChild(new TcosmaticButton(
                    centerX - 60, this.height / 2 - 30, 50, 50,
                    Text.literal(""), button -> resumeRecording(), 1
                ));
            }
            
            // Stop Button
            this.addDrawableChild(new TcosmaticButton(
                centerX + 10, this.height / 2 - 30, 50, 50,
                Text.literal(""), button -> stopRecording(), 3
            ));
        }
        
        // Stats Display
        addStatsPanel();
    }
    
    private void startRecording() {
        ReplayClient.getInstance().startRecording();
        isRecording = true;
        this.clearAndInit();
    }
    
    private void pauseRecording() {
        ReplayClient.getInstance().pauseRecording();
        isPaused = true;
        this.clearAndInit();
    }
    
    private void resumeRecording() {
        ReplayClient.getInstance().resumeRecording();
        isPaused = false;
        this.clearAndInit();
    }
    
    private void stopRecording() {
        ReplayClient.getInstance().stopRecording();
        isRecording = false;
        this.clearAndInit();
    }
    
    private void addStatsPanel() {
        // Add recording stats here
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        
        // Title
        context.drawCenteredTextWithShadow(this.textRenderer, 
            "🎬 Tcosmatic Replay Dashboard", this.width / 2, 20, 0xFFAA00);
        
        // Recording status
        String status = isRecording ? (isPaused ? "⏸️ PAUSED" : "⏺️ RECORDING") : "⚫ IDLE";
        context.drawCenteredTextWithShadow(this.textRenderer, 
            status, this.width / 2, 40, isRecording ? 0xFF3333 : 0x666666);
        
        super.render(context, mouseX, mouseY, delta);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollHandler.scroll(verticalAmount);
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
}
