package com.tcosmatic.replay.client.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.tcosmatic.replay.common.ReplayMetadata;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class FileManager {
    private static FileManager instance;
    private final Path basePath;
    private final Gson gson;
    
    private FileManager() {
        this.basePath = Paths.get("Tcosmatic");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        createDirectories();
    }
    
    public static FileManager getInstance() {
        if (instance == null) {
            instance = new FileManager();
        }
        return instance;
    }
    
    private void createDirectories() {
        createDir(basePath);
        createDir(basePath.resolve("replays"));
        createDir(basePath.resolve("exports"));
        createDir(basePath.resolve("temp"));
        createDir(basePath.resolve("cache"));
    }
    
    private void createDir(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public List<ReplayMetadata> loadAllReplays() {
        List<ReplayMetadata> replays = new ArrayList<>();
        Path replaysDir = basePath.resolve("replays");
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(replaysDir, "*.meta")) {
            for (Path entry : stream) {
                try (Reader reader = Files.newBufferedReader(entry)) {
                    ReplayMetadata meta = gson.fromJson(reader, ReplayMetadata.class);
                    replays.add(meta);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        // Sort by date (newest first)
        replays.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        return replays;
    }
    
    public void saveReplay(ReplayMetadata metadata, byte[] data) {
        try {
            String id = UUID.randomUUID().toString();
            metadata.setId(id);
            
            // Save metadata
            Path metaPath = basePath.resolve("replays/" + id + ".meta");
            try (Writer writer = Files.newBufferedWriter(metaPath)) {
                gson.toJson(metadata, writer);
            }
            
            // Save replay data
            Path dataPath = basePath.resolve("replays/" + id + ".tcos");
            Files.write(dataPath, data);
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void deleteReplay(ReplayMetadata metadata) {
        try {
            Files.deleteIfExists(basePath.resolve("replays/" + metadata.getId() + ".meta"));
            Files.deleteIfExists(basePath.resolve("replays/" + metadata.getId() + ".tcos"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
