package com.tcosmatic.replay.client.gui;

import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import com.tcosmatic.replay.TcosmaticReplayMod;
import java.util.function.Consumer;

public class TcosmaticButton extends ButtonWidget {
    private final int iconType; // 0=dashboard, 1=record, 2=pause, 3=stop, 4=replay
    private boolean isSelected = false;
    
    public TcosmaticButton(int x, int y, int width, int height, Text message, 
                          PressAction onPress, int iconType) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
        this.iconType = iconType;
    }
    
    @Override
    public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        // Background with hover effect
        int bgColor = isHovered() ? 0xAA666666 : (isSelected ? 0xAA444444 : 0xAA222222);
        context.fill(this.getX(), this.getY(), 
                     this.getX() + this.width, this.getY() + this.height, bgColor);
        
        // Border
        context.drawBorder(this.getX(), this.getY(), this.width, this.height, 
                          isSelected ? 0xFFFFAA00 : 0xFFFFFFFF);
        
        // Draw appropriate icon
        int iconSize = Math.min(width, height) - 6;
        int iconX = getX() + (width - iconSize) / 2;
        int iconY = getY() + (height - iconSize) / 2;
        
        switch(iconType) {
            case 0: TcosmaticIcons.drawDashboardIcon(context, iconX, iconY, iconSize); break;
            case 1: TcosmaticIcons.drawRecordIcon(context, iconX, iconY, iconSize); break;
            case 2: TcosmaticIcons.drawPauseIcon(context, iconX, iconY, iconSize); break;
            case 3: TcosmaticIcons.drawStopIcon(context, iconX, iconY, iconSize); break;
            case 4: TcosmaticIcons.drawReplayIcon(context, iconX, iconY, iconSize); break;
        }
    }
}
