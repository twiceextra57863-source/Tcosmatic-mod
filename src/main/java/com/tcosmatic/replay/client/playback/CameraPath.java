package com.tcosmatic.replay.client.playback;

import com.tcosmatic.replay.TcosmaticReplayMod;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

public class CameraPath {
    private final ConcurrentSkipListMap<Long, PathPoint> pathPoints;
    private boolean isActive = false;
    private PathStyle style = PathStyle.SMOOTH;
    private float pathSpeed = 1.0f;
    
    public enum PathStyle {
        SMOOTH,      // Smooth curves
        LINEAR,      // Straight lines between points
        BEZIER,      // Bezier curves
        CATMULL_ROM  // Catmull-Rom splines
    }
    
    public CameraPath() {
        this.pathPoints = new ConcurrentSkipListMap<>();
    }
    
    public void addKeyframe(long frame, float x, float y, float z, float yaw, float pitch) {
        PathPoint point = new PathPoint(frame, x, y, z, yaw, pitch);
        pathPoints.put(frame, point);
        TcosmaticReplayMod.LOGGER.info("Path point added at frame {}", frame);
    }
    
    public void removeKeyframe(long frame) {
        pathPoints.remove(frame);
    }
    
    public PathPoint getPointAtFrame(long frame) {
        return pathPoints.get(frame);
    }
    
    public PathPoint getInterpolatedPoint(long frame) {
        Map.Entry<Long, PathPoint> floor = pathPoints.floorEntry(frame);
        Map.Entry<Long, PathPoint> ceil = pathPoints.ceilingEntry(frame);
        
        if (floor == null && ceil == null) return null;
        if (floor == null) return ceil.getValue();
        if (ceil == null) return floor.getValue();
        if (floor.getKey().equals(ceil.getKey())) return floor.getValue();
        
        float t = (float)(frame - floor.getKey()) / (ceil.getKey() - floor.getKey());
        
        switch(style) {
            case LINEAR:
                return interpolateLinear(floor.getValue(), ceil.getValue(), t);
            case SMOOTH:
                return interpolateSmooth(floor.getValue(), ceil.getValue(), t);
            case BEZIER:
                return interpolateBezier(getBezierPoints(floor.getKey()), t);
            case CATMULL_ROM:
                return interpolateCatmullRom(getCatmullPoints(floor.getKey()), t);
            default:
                return interpolateLinear(floor.getValue(), ceil.getValue(), t);
        }
    }
    
    private PathPoint interpolateLinear(PathPoint p1, PathPoint p2, float t) {
        PathPoint result = new PathPoint(0, 0, 0, 0, 0, 0);
        result.x = p1.x + (p2.x - p1.x) * t;
        result.y = p1.y + (p2.y - p1.y) * t;
        result.z = p1.z + (p2.z - p1.z) * t;
        result.yaw = lerpAngle(p1.yaw, p2.yaw, t);
        result.pitch = p1.pitch + (p2.pitch - p1.pitch) * t;
        return result;
    }
    
    private PathPoint interpolateSmooth(PathPoint p1, PathPoint p2, float t) {
        // Smoothstep interpolation
        float st = t * t * (3 - 2 * t);
        return interpolateLinear(p1, p2, st);
    }
    
    private PathPoint interpolateBezier(List<PathPoint> points, float t) {
        if (points.size() < 4) return points.get(0);
        
        // Cubic Bezier
        float mt = 1 - t;
        float mt2 = mt * mt;
        float t2 = t * t;
        
        PathPoint p0 = points.get(0);
        PathPoint p1 = points.get(1);
        PathPoint p2 = points.get(2);
        PathPoint p3 = points.get(3);
        
        PathPoint result = new PathPoint(0, 0, 0, 0, 0, 0);
        result.x = mt2 * mt * p0.x + 3 * mt2 * t * p1.x + 3 * mt * t2 * p2.x + t2 * t * p3.x;
        result.y = mt2 * mt * p0.y + 3 * mt2 * t * p1.y + 3 * mt * t2 * p2.y + t2 * t * p3.y;
        result.z = mt2 * mt * p0.z + 3 * mt2 * t * p1.z + 3 * mt * t2 * p2.z + t2 * t * p3.z;
        
        return result;
    }
    
    private PathPoint interpolateCatmullRom(List<PathPoint> points, float t) {
        if (points.size() < 4) return points.get(1);
        
        // Catmull-Rom spline
        PathPoint p0 = points.get(0);
        PathPoint p1 = points.get(1);
        PathPoint p2 = points.get(2);
        PathPoint p3 = points.get(3);
        
        float t2 = t * t;
        float t3 = t2 * t;
        
        PathPoint result = new PathPoint(0, 0, 0, 0, 0, 0);
        result.x = 0.5f * ((-p0.x + 3*p1.x - 3*p2.x + p3.x) * t3 +
                           (2*p0.x - 5*p1.x + 4*p2.x - p3.x) * t2 +
                           (-p0.x + p2.x) * t +
                           2*p1.x);
        
        result.y = 0.5f * ((-p0.y + 3*p1.y - 3*p2.y + p3.y) * t3 +
                           (2*p0.y - 5*p1.y + 4*p2.y - p3.y) * t2 +
                           (-p0.y + p2.y) * t +
                           2*p1.y);
        
        result.z = 0.5f * ((-p0.z + 3*p1.z - 3*p2.z + p3.z) * t3 +
                           (2*p0.z - 5*p1.z + 4*p2.z - p3.z) * t2 +
                           (-p0.z + p2.z) * t +
                           2*p1.z);
        
        return result;
    }
    
    private float lerpAngle(float a, float b, float t) {
        float diff = ((b - a + 540) % 360) - 180;
        return a + diff * t;
    }
    
    private List<PathPoint> getBezierPoints(long frame) {
        List<PathPoint> points = new ArrayList<>();
        Map.Entry<Long, PathPoint> prev = pathPoints.lowerEntry(frame);
        Map.Entry<Long, PathPoint> curr = pathPoints.ceilingEntry(frame);
        Map.Entry<Long, PathPoint> next = pathPoints.higherEntry(curr.getKey());
        Map.Entry<Long, PathPoint> next2 = next != null ? pathPoints.higherEntry(next.getKey()) : null;
        
        points.add(prev != null ? prev.getValue() : curr.getValue());
        points.add(curr.getValue());
        points.add(next != null ? next.getValue() : curr.getValue());
        points.add(next2 != null ? next2.getValue() : (next != null ? next.getValue() : curr.getValue()));
        
        return points;
    }
    
    private List<PathPoint> getCatmullPoints(long frame) {
        List<PathPoint> points = new ArrayList<>();
        Map.Entry<Long, PathPoint> prev2 = pathPoints.lowerEntry(frame - 1);
        Map.Entry<Long, PathPoint> prev = pathPoints.lowerEntry(frame);
        Map.Entry<Long, PathPoint> curr = pathPoints.ceilingEntry(frame);
        Map.Entry<Long, PathPoint> next = pathPoints.higherEntry(curr.getKey());
        
        points.add(prev2 != null ? prev2.getValue() : (prev != null ? prev.getValue() : curr.getValue()));
        points.add(prev != null ? prev.getValue() : curr.getValue());
        points.add(curr.getValue());
        points.add(next != null ? next.getValue() : curr.getValue());
        
        return points;
    }
    
    public void activate() { isActive = true; }
    public void deactivate() { isActive = false; }
    public boolean isActive() { return isActive; }
    public void setStyle(PathStyle style) { this.style = style; }
    public void setSpeed(float speed) { this.pathSpeed = speed; }
    public void clear() { pathPoints.clear(); }
    
    public static class PathPoint {
        public long frame;
        public float x, y, z;
        public float yaw, pitch;
        
        public PathPoint(long frame, float x, float y, float z, float yaw, float pitch) {
            this.frame = frame;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }
                            }
