package com.tcosmatic.replay.client.gui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import com.tcosmatic.replay.client.utils.FileManager;
import com.tcosmatic.replay.common.ReplayMetadata;
import java.util.List;

public class ReplayBrowserScreen extends Screen {
    private final Screen parent;
    private List<ReplayMetadata> replays;
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    
    public ReplayBrowserScreen(Screen parent) {
        super(Text.literal("Your Replays"));
        this.parent = parent;
        this.replays = FileManager.getInstance().loadAllReplays();
    }
    
    @Override
    protected void init() {
        // Back button
        this.addDrawableChild(new TcosmaticButton(
            10, 10, 60, 20,
            Text.literal("← Back"), button -> client.setScreen(parent), 4
        ));
        
        // Export button (if replay selected)
        if (selectedIndex >= 0) {
            this.addDrawableChild(new TcosmaticButton(
                this.width - 80, 10, 70, 20,
                Text.literal("Export"), button -> openExportScreen(), 0
            ));
        }
        
        // Delete button
        if (selectedIndex >= 0) {
            this.addDrawableChild(new TcosmaticButton(
                this.width - 160, 10, 70, 20,
                Text.literal("Delete"), button -> deleteReplay(), 3
            ));
        }
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        
        // Title
        context.drawCenteredTextWithShadow(this.textRenderer, 
            "📼 Tcosmatic Replays", this.width / 2, 30, 0xFFAA00);
        
        // Render replay list
        int startY = 60 + scrollOffset;
        for (int i = 0; i < replays.size(); i++) {
            ReplayMetadata meta = replays.get(i);
            int y = startY + i * 40;
            
            if (y > 60 && y < this.height - 40) {
                // Background
                boolean isSelected = (i == selectedIndex);
                int bgColor = isSelected ? 0xAA444444 : 0xAA222222;
                context.fill(50, y, this.width - 50, y + 35, bgColor);
                
                // Border
                context.drawBorder(50, y, this.width - 100, 35, 
                                  isSelected ? 0xFFFFAA00 : 0xFFFFFFFF);
                
                // Thumbnail (simple)
                TcosmaticIcons.drawReplayIcon(context, 60, y + 5, 25);
                
                // Metadata
                context.drawText(this.textRenderer, meta.getName(), 95, y + 5, 0xFFFFFF, true);
                context.drawText(this.textRenderer, 
                    meta.getDate() + " | " + meta.getDuration() + "s", 
                    95, y + 20, 0xAAAAAA, true);
            }
        }
        
        super.render(context, mouseX, mouseY, delta);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle replay selection
        int startY = 60 + scrollOffset;
        for (int i = 0; i < replays.size(); i++) {
            int y = startY + i * 40;
            if (mouseX >= 50 && mouseX <= this.width - 50 && 
                mouseY >= y && mouseY <= y + 35) {
                selectedIndex = i;
                this.clearAndInit();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    private void openExportScreen() {
        if (selectedIndex >= 0) {
            client.setScreen(new ExportScreen(this, replays.get(selectedIndex)));
        }
    }
    
    private void deleteReplay() {
        if (selectedIndex >= 0) {
            FileManager.getInstance().deleteReplay(replays.get(selectedIndex));
            replays.remove(selectedIndex);
            selectedIndex = -1;
            this.clearAndInit();
        }
    }
                                  }
