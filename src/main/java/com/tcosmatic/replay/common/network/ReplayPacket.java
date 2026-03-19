package com.tcosmatic.replay.common.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import com.tcosmatic.replay.TcosmaticReplayMod;
import java.util.*;

public class ReplayPacket {
    public static final Identifier RECORDING_START = new Identifier(TcosmaticReplayMod.MOD_ID, "recording_start");
    public static final Identifier RECORDING_STOP = new Identifier(TcosmaticReplayMod.MOD_ID, "recording_stop");
    public static final Identifier FRAME_DATA = new Identifier(TcosmaticReplayMod.MOD_ID, "frame_data");
    public static final Identifier SYNC_REQUEST = new Identifier(TcosmaticReplayMod.MOD_ID, "sync_request");
    public static final Identifier SYNC_RESPONSE = new Identifier(TcosmaticReplayMod.MOD_ID, "sync_response");
    
    public static class RecordingStartPacket {
        public final String replayId;
        public final long startTime;
        public final String worldName;
        
        public RecordingStartPacket(String replayId, long startTime, String worldName) {
            this.replayId = replayId;
            this.startTime = startTime;
            this.worldName = worldName;
        }
        
        public static void encode(RecordingStartPacket packet, PacketByteBuf buf) {
            buf.writeString(packet.replayId);
            buf.writeLong(packet.startTime);
            buf.writeString(packet.worldName);
        }
        
        public static RecordingStartPacket decode(PacketByteBuf buf) {
            return new RecordingStartPacket(
                buf.readString(),
                buf.readLong(),
                buf.readString()
            );
        }
    }
    
    public static class RecordingStopPacket {
        public final String replayId;
        public final long endTime;
        public final int frameCount;
        
        public RecordingStopPacket(String replayId, long endTime, int frameCount) {
            this.replayId = replayId;
            this.endTime = endTime;
            this.frameCount = frameCount;
        }
        
        public static void encode(RecordingStopPacket packet, PacketByteBuf buf) {
            buf.writeString(packet.replayId);
            buf.writeLong(packet.endTime);
            buf.writeInt(packet.frameCount);
        }
        
        public static RecordingStopPacket decode(PacketByteBuf buf) {
            return new RecordingStopPacket(
                buf.readString(),
                buf.readLong(),
                buf.readInt()
            );
        }
    }
    
    public static class FrameDataPacket {
        public final String replayId;
        public final int frameNumber;
        public final byte[] compressedData;
        
        public FrameDataPacket(String replayId, int frameNumber, byte[] compressedData) {
            this.replayId = replayId;
            this.frameNumber = frameNumber;
            this.compressedData = compressedData;
        }
        
        public static void encode(FrameDataPacket packet, PacketByteBuf buf) {
            buf.writeString(packet.replayId);
            buf.writeInt(packet.frameNumber);
            buf.writeInt(packet.compressedData.length);
            buf.writeBytes(packet.compressedData);
        }
        
        public static FrameDataPacket decode(PacketByteBuf buf) {
            String replayId = buf.readString();
            int frameNumber = buf.readInt();
            int length = buf.readInt();
            byte[] data = new byte[length];
            buf.readBytes(data);
            return new FrameDataPacket(replayId, frameNumber, data);
        }
    }
    
    public static class SyncRequestPacket {
        public final String replayId;
        public final int startFrame;
        public final int endFrame;
        
        public SyncRequestPacket(String replayId, int startFrame, int endFrame) {
            this.replayId = replayId;
            this.startFrame = startFrame;
            this.endFrame = endFrame;
        }
        
        public static void encode(SyncRequestPacket packet, PacketByteBuf buf) {
            buf.writeString(packet.replayId);
            buf.writeInt(packet.startFrame);
            buf.writeInt(packet.endFrame);
        }
        
        public static SyncRequestPacket decode(PacketByteBuf buf) {
            return new SyncRequestPacket(
                buf.readString(),
                buf.readInt(),
                buf.readInt()
            );
        }
    }
    
    public static class SyncResponsePacket {
        public final String replayId;
        public final int frameCount;
        public final List<Integer> missingFrames;
        
        public SyncResponsePacket(String replayId, int frameCount, List<Integer> missingFrames) {
            this.replayId = replayId;
            this.frameCount = frameCount;
            this.missingFrames = missingFrames;
        }
        
        public static void encode(SyncResponsePacket packet, PacketByteBuf buf) {
            buf.writeString(packet.replayId);
            buf.writeInt(packet.frameCount);
            buf.writeInt(packet.missingFrames.size());
            for (int frame : packet.missingFrames) {
                buf.writeInt(frame);
            }
        }
        
        public static SyncResponsePacket decode(PacketByteBuf buf) {
            String replayId = buf.readString();
            int frameCount = buf.readInt();
            int missingCount = buf.readInt();
            List<Integer> missingFrames = new ArrayList<>();
            for (int i = 0; i < missingCount; i++) {
                missingFrames.add(buf.readInt());
            }
            return new SyncResponsePacket(replayId, frameCount, missingFrames);
        }
    }
}
