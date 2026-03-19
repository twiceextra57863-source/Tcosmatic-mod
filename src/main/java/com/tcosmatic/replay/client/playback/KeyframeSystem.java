package com.tcosmatic.replay.client.playback;

import com.tcosmatic.replay.TcosmaticReplayMod;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class KeyframeSystem {
    private final List<Keyframe> keyframes;
    private final List<KeyframeListener> listeners;
    private Keyframe selectedKeyframe;
    private InterpolationType defaultInterpolation = InterpolationType.CUBIC;
    
    public enum InterpolationType {
        LINEAR,      // Straight line
        SMOOTH,      // Smooth curve
        CUBIC,       // Cubic bezier
        EASE_IN,     // Slow in, fast out
        EASE_OUT,    // Fast in, slow out
        EASE_IN_OUT  // Smooth both ends
    }
    
    public KeyframeSystem() {
        this.keyframes = new CopyOnWriteArrayList<>();
        this.listeners = new CopyOnWriteArrayList<>();
    }
    
    public void addKeyframe(Keyframe keyframe) {
        keyframes.add(keyframe);
        sortKeyframes();
        notifyKeyframeAdded(keyframe);
        TcosmaticReplayMod.LOGGER.info("Keyframe added at frame {}", keyframe.frame);
    }
    
    public void removeKeyframe(int index) {
        if (index >= 0 && index < keyframes.size()) {
            Keyframe removed = keyframes.remove(index);
            notifyKeyframeRemoved(removed);
        }
    }
    
    public void updateKeyframe(int index, Keyframe newData) {
        if (index >= 0 && index < keyframes.size()) {
            Keyframe old = keyframes.set(index, newData);
            sortKeyframes();
            notifyKeyframeUpdated(old, newData);
        }
    }
    
    private void sortKeyframes() {
        keyframes.sort(Comparator.comparingLong(kf -> kf.frame));
    }
    
    public Keyframe getKeyframeAtFrame(long frame) {
        for (Keyframe kf : keyframes) {
            if (kf.frame == frame) return kf;
        }
        return null;
    }
    
    public Keyframe getPreviousKeyframe(long frame) {
        Keyframe prev = null;
        for (Keyframe kf : keyframes) {
            if (kf.frame <= frame) {
                prev = kf;
            } else {
                break;
            }
        }
        return prev;
    }
    
    public Keyframe getNextKeyframe(long frame) {
        for (Keyframe kf : keyframes) {
            if (kf.frame > frame) {
                return kf;
            }
        }
        return null;
    }
    
    public CameraTransform interpolate(long frame) {
        Keyframe prev = getPreviousKeyframe(frame);
        Keyframe next = getNextKeyframe(frame);
        
        if (prev == null && next == null) return null;
        if (prev == null) return new CameraTransform(next);
        if (next == null) return new CameraTransform(prev);
        if (prev.frame == next.frame) return new CameraTransform(prev);
        
        float t = (float)(frame - prev.frame) / (next.frame - prev.frame);
        
        InterpolationType type = prev.interpolationType;
        float easedT = ease(t, type);
        
        CameraTransform result = new CameraTransform();
        
        // Interpolate position
        result.x = lerp(prev.x, next.x, easedT);
        result.y = lerp(prev.y, next.y, easedT);
        result.z = lerp(prev.z, next.z, easedT);
        
        // Interpolate rotation (with shortest path)
        result.yaw = lerpAngle(prev.yaw, next.yaw, easedT);
        result.pitch = lerp(prev.pitch, next.pitch, easedT);
        
        return result;
    }
    
    private float ease(float t, InterpolationType type) {
        switch(type) {
            case LINEAR:
                return t;
            case SMOOTH:
                return t * t * (3 - 2 * t);
            case CUBIC:
                return t * t * t;
            case EASE_IN:
                return 1 - (float)Math.cos(t * Math.PI / 2);
            case EASE_OUT:
                return (float)Math.sin(t * Math.PI / 2);
            case EASE_IN_OUT:
                return (float)(1 - Math.cos(t * Math.PI)) / 2;
            default:
                return t;
        }
    }
    
    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
    
    private float lerpAngle(float a, float b, float t) {
        float diff = ((b - a + 540) % 360) - 180;
        return a + diff * t;
    }
    
    public void addListener(KeyframeListener listener) {
        listeners.add(listener);
    }
    
    private void notifyKeyframeAdded(Keyframe keyframe) {
        for (KeyframeListener listener : listeners) {
            listener.onKeyframeAdded(keyframe);
        }
    }
    
    private void notifyKeyframeRemoved(Keyframe keyframe) {
        for (KeyframeListener listener : listeners) {
            listener.onKeyframeRemoved(keyframe);
        }
    }
    
    private void notifyKeyframeUpdated(Keyframe old, Keyframe updated) {
        for (KeyframeListener listener : listeners) {
            listener.onKeyframeUpdated(old, updated);
        }
    }
    
    public static class Keyframe {
        public long frame;
        public float x, y, z;
        public float yaw, pitch;
        public InterpolationType interpolationType;
        public String name;
        public Map<String, Object> metadata;
        
        public Keyframe(long frame, float x, float y, float z, float yaw, float pitch) {
            this.frame = frame;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.interpolationType = InterpolationType.CUBIC;
            this.metadata = new HashMap<>();
        }
    }
    
    public static class CameraTransform {
        public float x, y, z;
        public float yaw, pitch;
        
        public CameraTransform() {}
        
        public CameraTransform(Keyframe kf) {
            this.x = kf.x;
            this.y = kf.y;
            this.z = kf.z;
            this.yaw = kf.yaw;
            this.pitch = kf.pitch;
        }
    }
    
    public interface KeyframeListener {
        void onKeyframeAdded(Keyframe keyframe);
        void onKeyframeRemoved(Keyframe keyframe);
        void onKeyframeUpdated(Keyframe old, Keyframe updated);
    }
          }
