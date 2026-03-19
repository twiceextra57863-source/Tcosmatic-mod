package com.tcosmatic.replay.client.recording;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.render.*;
import net.minecraft.util.Util;
import com.tcosmatic.replay.TcosmaticReplayMod;
import com.tcosmatic.replay.client.utils.CompressionUtil;

import java.io.*;
import java.nio.*;
import java.util.concurrent.CompletableFuture;

public class FrameCapture {
    private final MinecraftClient client;
    private NativeImage currentFrame;
    private boolean isCapturing = false;
    private FrameCaptureCallback callback;
    
    // Capture settings
    private int targetWidth = 1920;
    private int targetHeight = 1080;
    private boolean captureDiff = true; // Only capture changed pixels
    private boolean useGPU = true; // Use GPU for faster capture
    
    // Performance metrics
    private long lastCaptureTime;
    private float captureTimeMs;
    
    public interface FrameCaptureCallback {
        void onFrameCaptured(NativeImage frame, long timestamp);
        void onCaptureError(Exception e);
    }
    
    public FrameCapture() {
        this.client = MinecraftClient.getInstance();
    }
    
    public CompletableFuture<NativeImage> captureFrameAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long startTime = System.nanoTime();
                
                // Capture using Minecraft's screenshot system
                NativeImage image = captureScreen();
                
                // Resize if needed
                if (image.getWidth() != targetWidth || image.getHeight() != targetHeight) {
                    image = resizeImage(image, targetWidth, targetHeight);
                }
                
                // Calculate capture time
                captureTimeMs = (System.nanoTime() - startTime) / 1_000_000.0f;
                lastCaptureTime = System.currentTimeMillis();
                
                return image;
                
            } catch (Exception e) {
                TcosmaticReplayMod.LOGGER.error("Frame capture failed", e);
                throw new RuntimeException(e);
            }
        });
    }
    
    private NativeImage captureScreen() {
        // Use Minecraft's screenshot system
        return ScreenshotRecorder.takeScreenshot(client.getFramebuffer());
    }
    
    private NativeImage resizeImage(NativeImage original, int width, int height) {
        NativeImage resized = new NativeImage(width, height, false);
        
        // Simple bilinear resize
        float xScale = (float)original.getWidth() / width;
        float yScale = (float)original.getHeight() / height;
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int srcX = (int)(x * xScale);
                int srcY = (int)(y * yScale);
                int color = original.getColor(srcX, srcY);
                resized.setColor(x, y, color);
            }
        }
        
        original.close();
        return resized;
    }
    
    public byte[] compressFrame(NativeImage frame) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            
            // Write header
            baos.write(frame.getWidth() >> 8);
            baos.write(frame.getWidth() & 0xFF);
            baos.write(frame.getHeight() >> 8);
            baos.write(frame.getHeight() & 0xFF);
            
            // Convert to RGB and compress
            ByteBuffer buffer = ByteBuffer.allocate(frame.getWidth() * frame.getHeight() * 3);
            
            for (int y = 0; y < frame.getHeight(); y++) {
                for (int x = 0; x < frame.getWidth(); x++) {
                    int color = frame.getColor(x, y);
                    buffer.put((byte)((color >> 16) & 0xFF)); // R
                    buffer.put((byte)((color >> 8) & 0xFF));  // G
                    buffer.put((byte)(color & 0xFF));         // B
                }
            }
            
            buffer.flip();
            byte[] rgbData = new byte[buffer.remaining()];
            buffer.get(rgbData);
            
            // Compress with high quality
            return CompressionUtil.compressImage(rgbData, frame.getWidth(), frame.getHeight());
            
        } catch (Exception e) {
            TcosmaticReplayMod.LOGGER.error("Frame compression failed", e);
            return new byte[0];
        }
    }
    
    public NativeImage decompressFrame(byte[] data) {
        try {
            // Read header
            int width = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
            int height = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
            
            // Decompress image data
            byte[] rgbData = CompressionUtil.decompressImage(data, 4);
            
            // Create NativeImage
            NativeImage image = new NativeImage(width, height, false);
            
            ByteBuffer buffer = ByteBuffer.wrap(rgbData);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int r = buffer.get() & 0xFF;
                    int g = buffer.get() & 0xFF;
                    int b = buffer.get() & 0xFF;
                    int color = (0xFF << 24) | (r << 16) | (g << 8) | b;
                    image.setColor(x, y, color);
                }
            }
            
            return image;
            
        } catch (Exception e) {
            TcosmaticReplayMod.LOGGER.error("Frame decompression failed", e);
            return null;
        }
    }
    
    public byte[] captureDiff(NativeImage previous, NativeImage current) {
        if (previous == null || current == null) return null;
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        
        try {
            int changedPixels = 0;
            
            for (int y = 0; y < current.getHeight(); y++) {
                for (int x = 0; x < current.getWidth(); x++) {
                    int prevColor = previous.getColor(x, y);
                    int currColor = current.getColor(x, y);
                    
                    if (prevColor != currColor) {
                        changedPixels++;
                        
                        // Write position and color
                        dos.writeShort(x);
                        dos.writeShort(y);
                        dos.writeInt(currColor);
                    }
                }
            }
            
            // Write header with changed pixel count
            ByteArrayOutputStream header = new ByteArrayOutputStream();
            DataOutputStream headerOut = new DataOutputStream(header);
            headerOut.writeInt(changedPixels);
            headerOut.writeInt(current.getWidth());
            headerOut.writeInt(current.getHeight());
            
            // Combine header and diff data
            byte[] headerBytes = header.toByteArray();
            byte[] diffBytes = baos.toByteArray();
            
            byte[] result = new byte[headerBytes.length + diffBytes.length];
            System.arraycopy(headerBytes, 0, result, 0, headerBytes.length);
            System.arraycopy(diffBytes, 0, result, headerBytes.length, diffBytes.length);
            
            return result;
            
        } catch (IOException e) {
            TcosmaticReplayMod.LOGGER.error("Diff capture failed", e);
            return null;
        }
    }
    
    public void setTargetResolution(int width, int height) {
        this.targetWidth = width;
        this.targetHeight = height;
    }
    
    public float getCaptureTimeMs() { return captureTimeMs; }
    public long getLastCaptureTime() { return lastCaptureTime; }
}
