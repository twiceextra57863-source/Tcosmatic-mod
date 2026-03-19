package com.tcosmatic.replay.client.gui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import com.tcosmatic.replay.TcosmaticReplayMod;
import com.tcosmatic.replay.common.ReplayMetadata;
import com.tcosmatic.replay.client.utils.FFmpegExporter;
import com.tcosmatic.replay.client.utils.ScrollHandler;

import java.util.concurrent.CompletableFuture;

public class ExportScreen extends Screen {
    private final Screen parent;
    private final ReplayMetadata replay;
    private final FFmpegExporter exporter;
    private final ScrollHandler scrollHandler;
    
    // Export settings
    private int selectedFPS = 60;
    private int selectedWidth = 1920;
    private int selectedHeight = 1080;
    private int selectedQuality = 18; // CRF
    private String selectedCodec = "libx264";
    private boolean selectedAudio = false;
    
    // UI components
    private TextFieldWidget filenameField;
    private ButtonWidget exportButton;
    private ButtonWidget cancelButton;
    private SliderWidget fpsSlider;
    private SliderWidget qualitySlider;
    
    // Export state
    private boolean isExporting = false;
    private CompletableFuture<Void> exportFuture;
    private FFmpegExporter.ExportProgress progress;
    
    // Scroll handling
    private int scrollY = 0;
    private final int contentHeight = 400;
    
    public ExportScreen(Screen parent, ReplayMetadata replay) {
        super(Text.literal("Export Replay"));
        this.parent = parent;
        this.replay = replay;
        this.exporter = new FFmpegExporter();
        this.scrollHandler = ScrollHandler.getInstance();
    }
    
    @Override
    protected void init() {
        int centerX = this.width / 2;
        
        // Filename input
        this.filenameField = new TextFieldWidget(
            this.textRenderer,
            centerX - 150, 50 + scrollY, 300, 20,
            Text.literal("Filename")
        );
        filenameField.setText(replay.getName());
        filenameField.setMaxLength(50);
        this.addDrawableChild(filenameField);
        
        // FPS Slider
        this.fpsSlider = new SliderWidget(
            centerX - 150, 80 + scrollY, 300, 20,
            Text.literal("FPS: " + selectedFPS), selectedFPS / 120.0
        ) {
            @Override
            protected void updateMessage() {
                this.setMessage(Text.literal("FPS: " + selectedFPS));
            }
            
            @Override
            protected void applyValue() {
                selectedFPS = 30 + (int)(this.value * 90); // 30-120 FPS
            }
        };
        this.addDrawableChild(fpsSlider);
        
        // Resolution buttons
        addResolutionButtons(centerX, 110 + scrollY);
        
        // Quality Slider
        this.qualitySlider = new SliderWidget(
            centerX - 150, 170 + scrollY, 300, 20,
            Text.literal("Quality: Best"), 0
        ) {
            @Override
            protected void updateMessage() {
                String qualityText;
                if (selectedQuality <= 18) qualityText = "Best";
                else if (selectedQuality <= 23) qualityText = "High";
                else if (selectedQuality <= 28) qualityText = "Medium";
                else qualityText = "Low";
                
                this.setMessage(Text.literal("Quality: " + qualityText));
            }
            
            @Override
            protected void applyValue() {
                selectedQuality = 18 + (int)(this.value * 33); // 18-51 CRF
            }
        };
        this.addDrawableChild(qualitySlider);
        
        // Codec selection
        addCodecButtons(centerX, 200 + scrollY);
        
        // Audio toggle
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Include Audio: " + (selectedAudio ? "✅" : "❌")),
            button -> {
                selectedAudio = !selectedAudio;
                button.setMessage(Text.literal("Include Audio: " + (selectedAudio ? "✅" : "❌")));
            }
        ).dimensions(centerX - 150, 230 + scrollY, 300, 20).build());
        
        // Export/Cancel buttons
        this.exportButton = ButtonWidget.builder(
            Text.literal("📤 Start Export"),
            button -> startExport()
        ).dimensions(centerX - 160, 270 + scrollY, 150, 20).build();
        
        this.cancelButton = ButtonWidget.builder(
            Text.literal("❌ Cancel"),
            button -> client.setScreen(parent)
        ).dimensions(centerX + 10, 270 + scrollY, 150, 20).build();
        
        this.addDrawableChild(exportButton);
        this.addDrawableChild(cancelButton);
        
        // Estimated file size
        addEstimatedSize(centerX, 300 + scrollY);
    }
    
    private void addResolutionButtons(int centerX, int y) {
        int[][] resolutions = {
            {854, 480},   // 480p
            {1280, 720},  // 720p
            {1920, 1080}, // 1080p
            {2560, 1440}, // 1440p
            {3840, 2160}  // 4K
        };
        
        String[] labels = {"480p", "720p", "1080p", "1440p", "4K"};
        
        for (int i = 0; i < resolutions.length; i++) {
            final int w = resolutions[i][0];
            final int h = resolutions[i][1];
            
            int x = centerX - 150 + (i * 60);
            
            this.addDrawableChild(ButtonWidget.builder(
                Text.literal(labels[i]),
                button -> {
                    selectedWidth = w;
                    selectedHeight = h;
                }
            ).dimensions(x, y, 55, 20).build());
        }
    }
    
    private void addCodecButtons(int centerX, int y) {
        String[] codecs = {"H.264", "H.265/HEVC", "VP9", "AV1"};
        String[] ffCodecs = {"libx264", "libx265", "libvpx-vp9", "libaom-av1"};
        
        for (int i = 0; i < codecs.length; i++) {
            final String codec = ffCodecs[i];
            
            int x = centerX - 150 + (i * 75);
            
            this.addDrawableChild(ButtonWidget.builder(
                Text.literal(codecs[i]),
                button -> selectedCodec = codec
            ).dimensions(x, y, 70, 20).build());
        }
    }
    
    private void addEstimatedSize(int centerX, int y) {
        // Calculate rough estimate
        double bitrate = (selectedWidth * selectedHeight * selectedFPS * 0.1) / 1000000.0; // Mbps
        long durationSeconds = replay.getDuration() / 1000;
        double estimatedMB = (bitrate * durationSeconds) / 8;
        
        String estimate = String.format("Estimated size: %.1f MB (%.1f Mbps)", 
            estimatedMB, bitrate);
        
        // Store for rendering
        sessionData.put("estimate", estimate);
    }
    
    private void startExport() {
        isExporting = true;
        exportButton.active = false;
        
        // Create export settings
        FFmpegExporter.ExportSettings settings = new FFmpegExporter.ExportSettings();
        settings.fps = selectedFPS;
        settings.width = selectedWidth;
        settings.height = selectedHeight;
        settings.quality = selectedQuality;
        settings.codec = selectedCodec;
        settings.includeAudio = selectedAudio;
        
        // Start export
        progress = new FFmpegExporter.ExportProgress();
        exportFuture = exporter.exportReplay(replay, settings, progress)
            .thenAccept(file -> {
                TcosmaticReplayMod.LOGGER.info("Export complete: {}", file);
                isExporting = false;
            })
            .exceptionally(throwable -> {
                TcosmaticReplayMod.LOGGER.error("Export failed", throwable);
                progress.error(throwable.getMessage());
                isExporting = false;
                return null;
            });
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        
        // Title
        context.drawCenteredTextWithShadow(this.textRenderer, 
            "📤 Export Replay: " + replay.getName(), 
            this.width / 2, 20 + scrollY, 0xFFAA00);
        
        // Render export progress if exporting
        if (isExporting && progress != null) {
            renderProgress(context);
        }
        
        // Render estimate
        if (sessionData.containsKey("estimate")) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                (String)sessionData.get("estimate"),
                this.width / 2, 350 + scrollY, 0xAAAAAA);
        }
        
        super.render(context, mouseX, mouseY, delta);
    }
    
    private void renderProgress(DrawContext context) {
        int centerX = this.width / 2;
        int y = 150;
        
        // Progress bar background
        context.fill(centerX - 150, y, centerX + 150, y + 30, 0xFF333333);
        
        // Progress bar fill
        int progressWidth = (int)(300 * (progress.getPercentage() / 100.0));
        context.fill(centerX - 150, y, centerX - 150 + progressWidth, y + 30, 
            0xFF00AA00);
        
        // Percentage text
        String percentText = progress.getPercentage() + "%";
        context.drawCenteredTextWithShadow(this.textRenderer, 
            percentText, centerX, y + 10, 0xFFFFFF);
        
        // Status text
        context.drawCenteredTextWithShadow(this.textRenderer,
            progress.getStatus(),
            centerX, y + 40, 0xFFFFFF);
        
        // Cancel button during export
        context.drawCenteredTextWithShadow(this.textRenderer,
            "Click ESC to cancel",
            centerX, y + 60, 0xFFAA00);
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Handle scroll keys
        if (scrollHandler.handleKeyPress(keyCode)) {
            scrollY = scrollHandler.getOffset();
            this.clearAndInit(); // Refresh UI with new scroll position
            return true;
        }
        
        // ESC to cancel export
        if (keyCode == 256 && isExporting) {
            exportFuture.cancel(true);
            isExporting = false;
            exportButton.active = true;
            return true;
        }
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollHandler.scroll(verticalAmount);
        scrollY = scrollHandler.getOffset();
        this.clearAndInit(); // Refresh UI
        return true;
    }
    
    private Map<String, Object> sessionData = new HashMap<>();
                                        }
