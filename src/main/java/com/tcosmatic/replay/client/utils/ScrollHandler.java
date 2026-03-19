package com.tcosmatic.replay.client.utils;

public class ScrollHandler {
    private int scrollOffset = 0;
    private final int maxScroll = 1000;
    private final int scrollSpeed = 20;
    
    public void scroll(double amount) {
        scrollOffset += amount * scrollSpeed;
        clampScroll();
    }
    
    public void scrollUp() {
        scrollOffset -= scrollSpeed;
        clampScroll();
    }
    
    public void scrollDown() {
        scrollOffset += scrollSpeed;
        clampScroll();
    }
    
    private void clampScroll() {
        scrollOffset = Math.max(-maxScroll, Math.min(maxScroll, scrollOffset));
    }
    
    public int getOffset() {
        return scrollOffset;
    }
    
    public void reset() {
        scrollOffset = 0;
    }
}
