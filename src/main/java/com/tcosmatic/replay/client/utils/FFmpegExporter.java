package com.tcosmatic.replay.client.utils;

import com.tcosmatic.replay.TcosmaticReplayMod;
import com.tcosmatic.replay.common.ReplayMetadata;
import java.io.*;
import java.util.concurrent.CompletableFuture;

public class FFmpegExporter {
    private Process ffmpegProcess;
    private ExportProgress progress;
    
    public CompletableFuture<File> exportReplay(ReplayMetadata replay, ExportSettings settings) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                progress = new ExportProgress();
                
                // Create temp directory for frames
                File tempDir = new File("Tcosmatic/temp/" + replay.getId());
                tempDir.mkdirs();
                
                // Render frames
                renderFrames(replay, tempDir, settings, progress);
                
                // Build FFmpeg command
                ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg",
                    "-y", // Overwrite output
                    "-framerate", String.valueOf(settings.fps),
                    "-i", tempDir.getAbsolutePath() + "/frame_%06d.png",
                    "-c:v", settings.codec,
                    "-crf", String.valueOf(settings.quality),
                    "-pix_fmt", "yuv420p",
                    "-vf", "scale=" + settings.width + ":" + settings.height,
                    "Tcosmatic/exports/" + replay.getName() + ".mp4"
                );
                
                pb.redirectErrorStream(true);
                ffmpegProcess = pb.start();
                
                // Monitor progress
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(ffmpegProcess.getInputStream())
                );
                String line;
                while ((line = reader.readLine()) != null) {
                    updateProgressFromFFmpeg(line, progress);
                }
                
                int exitCode = ffmpegProcess.waitFor();
                if (exitCode == 0) {
                    progress.complete();
                    return new File("Tcosmatic/exports/" + replay.getName() + ".mp4");
                } else {
                    throw new RuntimeException("FFmpeg failed with code: " + exitCode);
                }
                
            } catch (Exception e) {
                TcosmaticReplayMod.LOGGER.error("Export failed", e);
                progress.error(e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }
    
    public static class ExportSettings {
        int fps = 60;
        int width = 1920;
        int height = 1080;
        int quality = 18; // CRF value (lower = better)
        String codec = "libx264";
        boolean includeAudio = false;
    }
    
    public static class ExportProgress {
        private int percentage = 0;
        private String status = "Starting...";
        private boolean isComplete = false;
        private String error = null;
        
        public void setProgress(int percent, String status) {
            this.percentage = percent;
            this.status = status;
        }
        
        public void complete() {
            this.isComplete = true;
            this.percentage = 100;
            this.status = "Complete!";
        }
        
        public void error(String error) {
            this.error = error;
            this.status = "Error: " + error;
        }
    }
}
