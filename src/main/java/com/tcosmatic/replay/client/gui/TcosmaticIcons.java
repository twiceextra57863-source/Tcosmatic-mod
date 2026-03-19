package com.tcosmatic.replay.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import com.tcosmatic.replay.TcosmaticReplayMod;

public class TcosmaticIcons {
    
    // 📊 Dashboard Icon (Tcosmatic Logo)
    public static void drawDashboardIcon(DrawContext context, int x, int y, int size) {
        int centerX = x + size/2;
        int centerY = y + size/2;
        
        // Outer circle (Red ring)
        context.drawBorder(x, y, size, size, 0xFFFF3333); // Bright red border
        context.fill(x+2, y+2, x+size-2, y+size-2, 0x33FF3333); // Semi-transparent red fill
        
        // Inner 'T' logo
        context.fill(centerX - size/4, centerY - size/3, 
                     centerX + size/4, centerY - size/3 + size/6, 0xFFFFFFFF); // Top bar
        context.fill(centerX - size/8, centerY - size/3, 
                     centerX + size/8, centerY + size/3, 0xFFFFFFFF); // Vertical line
    }
    
    // ▶️ Record Button
    public static void drawRecordIcon(DrawContext context, int x, int y, int size) {
        // Red circle with white border
        context.drawBorder(x, y, size, size, 0xFFFFFFFF);
        context.fill(x+3, y+3, x+size-3, y+size-3, 0xFFFF3333);
        
        // Play triangle
        int[] xPoints = {x + size/3, x + size*2/3, x + size/3};
        int[] yPoints = {y + size/3, y + size/2, y + size*2/3};
        for (int i = 0; i < 3; i++) {
            context.fill(xPoints[i], yPoints[i], xPoints[i]+2, yPoints[i]+2, 0xFFFFFFFF);
        }
    }
    
    // ⏸️ Pause Icon
    public static void drawPauseIcon(DrawContext context, int x, int y, int size) {
        context.fill(x + size/4, y + size/4, x + size/4 + size/6, y + size*3/4, 0xFFFFFFAA);
        context.fill(x + size*2/3, y + size/4, x + size*2/3 + size/6, y + size*3/4, 0xFFFFFFAA);
    }
    
    // ⏹️ Stop Icon
    public static void drawStopIcon(DrawContext context, int x, int y, int size) {
        context.fill(x + size/4, y + size/4, x + size*3/4, y + size*3/4, 0xFFFFAA33);
    }
    
    // 📼 Replay Icon
    public static void drawReplayIcon(DrawContext context, int x, int y, int size) {
        // Film strip
        context.fill(x, y, x+size, y+size/6, 0xFFAAAAAA);
        context.fill(x, y+size-size/6, x+size, y+size, 0xFFAAAAAA);
        
        // Perforations
        for (int i = 0; i < 4; i++) {
            context.fill(x + i*size/4 + size/8, y + size/12, 
                         x + i*size/4 + size/4, y + size/6, 0xFF000000);
        }
    }
}
