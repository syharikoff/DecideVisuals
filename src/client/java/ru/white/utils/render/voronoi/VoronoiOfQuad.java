package ru.white.utils.render.voronoi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class VoronoiOfQuad {
   private static final Random RANDOM = new Random();
   private static final int CANDIDATES_PER_POINT = 24;
   public final float x;
   public final float y;
   public final float x2;
   public final float y2;
   public final float cx;
   public final float cy;
   private final List<Polygon> polygons;

   public VoronoiOfQuad(float x, float y, float x2, float y2, List<Vec2f> points) {
      this.x = x;
      this.y = y;
      this.x2 = x2;
      this.y2 = y2;
      this.cx = x + (x2 - x) / 2.0F;
      this.cy = y + (y2 - y) / 2.0F;
      this.polygons = this.getVoronoiPolygons(x, y, x2, y2, points);
   }

   public VoronoiOfQuad(float x, float y, float x2, float y2, int n) {
      this(x, y, x2, y2, genPointsInBounds(x, y, x2, y2, n));
   }

   private static float clamp(float v, float min, float max) {
      return Math.max(min, Math.min(max, v));
   }

   public static VoronoiOfQuad spread(
      float x1, float y1, float x2, float y2,
      float bx1, float by1, float bx2, float by2,
      int count, float aspect, float relax, float jitter, float minDist, Random random
   ) {
      float spacing = (float)Math.sqrt((bx2 - bx1) * aspect * (by2 - by1) / Math.max(1, count));
      float minSpacing = spacing * minDist;
      List<Vec2f> points = genSpreadPointsInBounds(bx1, by1, bx2, by2, count, aspect, minSpacing, random);
      List<Polygon> relaxed;
      if (relax > 0.001F && (relaxed = new VoronoiOfQuad(x1, y1, x2, y2, points).getPolygons()).size() == points.size()) {
         for (int i = 0; i < points.size(); i++) {
            Vec2f gen = points.get(i);
            Vec2f cen = relaxed.get(i).center;
            gen.x = clamp(gen.x + (cen.x - gen.x) * relax, bx1, bx2);
            gen.y = clamp(gen.y + (cen.y - gen.y) * relax, by1, by2);
         }
      }
      if (jitter > 0.001F) {
         float jx = spacing * jitter / aspect;
         float jy = spacing * jitter;
         for (int i = 0; i < points.size(); i++) {
            Vec2f p = points.get(i);
            float nx = clamp(p.x + (random.nextFloat() * 2.0F - 1.0F) * jx, bx1, bx2);
            float ny = clamp(p.y + (random.nextFloat() * 2.0F - 1.0F) * jy, by1, by2);
            if (nearestDistance(points, i, nx, ny, aspect) >= minSpacing * minSpacing) {
               p.x = nx;
               p.y = ny;
            }
         }
      }
      return new VoronoiOfQuad(x1, y1, x2, y2, points);
   }

   public static VoronoiOfQuad spread(
      float x1, float y1, float x2, float y2,
      float bx1, float by1, float bx2, float by2,
      int count, float aspect, float relax, float jitter, float minDist
   ) {
      return spread(x1, y1, x2, y2, bx1, by1, bx2, by2, count, aspect, relax, jitter, minDist, RANDOM);
   }

   private static List<Vec2f> genPointsInBounds(float x1, float y1, float x2, float y2, int n) {
      ArrayList<Vec2f> list = new ArrayList<>(n);
      for (int i = 0; i < n; i++) {
         list.add(new Vec2f(x1 + RANDOM.nextFloat() * (x2 - x1), y1 + RANDOM.nextFloat() * (y2 - y1)));
      }
      return list;
   }

   public List<Polygon> getVoronoiPolygons(float x1, float y1, float x2, float y2, List<Vec2f> points) {
      ArrayList<Polygon> result = new ArrayList<>();
      List<Vec2f> bounds = Arrays.asList(
         new Vec2f(x1, y1), new Vec2f(x2, y1), new Vec2f(x2, y2), new Vec2f(x1, y2)
      );
      int n = points.size();
      for (int i = 0; i < n; i++) {
         List<Vec2f> poly = new ArrayList<>(bounds);
         int j = 0;
         while (j < n && (j == i || !(poly = clipPolygon(poly,
                2.0F * (points.get(j).x - points.get(i).x),
                2.0F * (points.get(j).y - points.get(i).y),
                points.get(i).x * points.get(i).x + points.get(i).y * points.get(i).y -
                (points.get(j).x * points.get(j).x + points.get(j).y * points.get(j).y)
             )).isEmpty())) {
            j++;
         }
         if (poly.size() >= 3) {
            result.add(new Polygon(new ArrayList<>(poly)));
         }
      }
      return result;
   }

   private List<Vec2f> clipPolygon(List<Vec2f> poly, float a, float b, float c) {
      ArrayList<Vec2f> out = new ArrayList<>();
      int n = poly.size();
      for (int i = 0; i < n; i++) {
         Vec2f cur = poly.get(i);
         Vec2f next = poly.get((i + 1) % n);
         boolean curIn = isInside(cur, a, b, c);
         boolean nextIn = isInside(next, a, b, c);
         if (curIn) {
            if (!nextIn) {
               Vec2f ix = findIntersection(cur, next, a, b, c);
               if (ix != null) out.add(ix);
            } else {
               out.add(next);
            }
         } else if (nextIn) {
            Vec2f ix = findIntersection(cur, next, a, b, c);
            if (ix != null) out.add(ix);
            out.add(next);
         }
      }
      return out;
   }

   private Vec2f findIntersection(Vec2f p1, Vec2f p2, float a, float b, float c) {
      float dx = p2.x - p1.x;
      float dy = p2.y - p1.y;
      float denom = a * dx + b * dy;
      if (Math.abs(denom) < 1.0E-4) return null;
      float t = -(a * p1.x + b * p1.y + c) / denom;
      return (t >= 0.0F && t <= 1.0F) ? new Vec2f(p1.x + t * dx, p1.y + t * dy) : null;
   }

   private static float nearestDistance(List<Vec2f> list, int skip, float x, float y, float aspect) {
      float best = Float.MAX_VALUE;
      for (int i = 0; i < list.size(); i++) {
         if (i != skip) {
            Vec2f p = list.get(i);
            float dx = (x - p.x) * aspect;
            float dy = y - p.y;
            best = Math.min(best, dx * dx + dy * dy);
         }
      }
      return best;
   }

   private static List<Vec2f> genSpreadPointsInBounds(float x1, float y1, float x2, float y2, int n, float aspect, float minSpacing, Random random) {
      ArrayList<Vec2f> list = new ArrayList<>(n);
      float minSq = minSpacing * minSpacing;
      for (int i = 0; i < n; i++) {
         Vec2f best = null;
         float bestDist = -1.0F;
         for (int j = 0; j < 24; j++) {
            Vec2f cand = new Vec2f(x1 + random.nextFloat() * (x2 - x1), y1 + random.nextFloat() * (y2 - y1));
            if (list.isEmpty()) { best = cand; bestDist = Float.MAX_VALUE; break; }
            float d = nearestDistance(list, -1, cand.x, cand.y, aspect);
            if (d > bestDist) { bestDist = d; best = cand; }
            if (bestDist >= minSq) break;
         }
         if (best != null && bestDist >= minSq) list.add(best);
      }
      return list;
   }

   private static float wrapDegrees(float f) {
      float r = f % 360.0F;
      if (r >= 180.0F) r -= 360.0F;
      if (r < -180.0F) r += 360.0F;
      return r;
   }

   private boolean isInside(Vec2f v, float a, float b, float c) {
      return a * v.x + b * v.y + c <= 0.0F;
   }

   public List<Polygon> getPolygons() { return this.polygons; }

   public static class Polygon {
      public Vec2f center;
      public final ArrayList<Vec2f> list;

      public Polygon(ArrayList<Vec2f> list) {
         this.list = list;
         this.center = this.getCenter();
      }

      public Polygon copy() {
         ArrayList<Vec2f> c = new ArrayList<>(list.size());
         for (Vec2f v : list) c.add(new Vec2f(v.x, v.y));
         return new Polygon(c);
      }

      public Polygon rotateAngleOfCenter(float angle) {
         if (angle == 0.0F) return this;
         for (Vec2f v : list) {
            float dx = v.x - center.x;
            float dy = v.y - center.y;
            float rad = (float)Math.toRadians(Math.toDegrees(Math.atan2(dy, dx)) + angle - 90.0);
            float dist = (float)Math.sqrt(dx * dx + dy * dy);
            v.x = center.x - (float)Math.sin(rad) * dist;
            v.y = center.y + (float)Math.cos(rad) * dist;
         }
         return this;
      }

      public Polygon moveXY(float dx, float dy) {
         center.x += dx;
         center.y += dy;
         for (Vec2f v : list) { v.x += dx; v.y += dy; }
         return this;
      }

      public Vec2f getCenter() {
         Vec2f c = new Vec2f(0, 0);
         for (Vec2f v : list) { c.x += v.x; c.y += v.y; }
         int n = Math.max(1, list.size());
         c.x /= n;
         c.y /= n;
         return c;
      }

      public List<Vec2f> getAllVertices() { return this.list; }
   }

   public static class Vec2f {
      public float x;
      public float y;
      public Vec2f(float x, float y) { this.x = x; this.y = y; }
   }
}
