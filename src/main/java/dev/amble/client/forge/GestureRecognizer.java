package dev.amble.client.forge;

import dev.amble.core.ringpowers.constructs.ConstructTool;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class GestureRecognizer {
    private static final int POINTS = 32;
    private static final int CLOSED_SHIFTS = 8;
    private static final float MATCH_THRESHOLD = 0.2F;

    private record Template(ConstructTool tool, List<float[]> points, boolean closed) {}

    private static final List<Template> TEMPLATES = List.of(
            template(ConstructTool.SWORD, false, 0, 0, 0, 1),
            template(ConstructTool.SPEAR, false, 0, 0, 1, 0),
            template(ConstructTool.PICKAXE, false, 0, 0, 0.5F, 1, 1, 0),
            template(ConstructTool.AXE, false, 0, 1, 1, 1, 0.3F, 0),
            template(ConstructTool.BATTLEAXE, false, 0, 1, 1, 1, 0, 0, 1, 0),
            template(ConstructTool.SHOVEL, false, 0, 1, 0, 0.35F, 0.15F, 0.08F, 0.5F, 0, 0.85F, 0.08F, 1, 0.35F, 1, 1),
            template(ConstructTool.HOE, false, 0, 1, 0, 0, 0.6F, 0),
            circle(ConstructTool.MACE),
            arc(ConstructTool.FLINT_AND_STEEL, 60.0F, 300.0F),
            template(ConstructTool.SHEARS, false, 0, 1, 0.5F, 0, 1, 1)
    );

    public static Optional<ConstructTool> recognize(List<float[]> stroke) {
        if (stroke.size() < 2) return Optional.empty();
        List<float[]> candidate = normalize(resample(stroke));

        ConstructTool best = null;
        float bestDistance = Float.MAX_VALUE;
        for (Template template : TEMPLATES) {
            float distance = distanceTo(candidate, template);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = template.tool();
            }
        }
        return bestDistance < MATCH_THRESHOLD ? Optional.ofNullable(best) : Optional.empty();
    }

    public static float length(List<float[]> stroke) {
        float length = 0.0F;
        for (int i = 1; i < stroke.size(); i++) length += distance(stroke.get(i - 1), stroke.get(i));
        return length;
    }

    private static float distanceTo(List<float[]> candidate, Template template) {
        List<float[]> forward = template.points();
        List<float[]> backward = new ArrayList<>(forward);
        Collections.reverse(backward);

        float best = Math.min(averageDistance(candidate, forward), averageDistance(candidate, backward));
        if (template.closed()) {
            for (int shift = 1; shift < CLOSED_SHIFTS; shift++) {
                int offset = shift * POINTS / CLOSED_SHIFTS;
                best = Math.min(best, averageDistance(candidate, rotate(forward, offset)));
                best = Math.min(best, averageDistance(candidate, rotate(backward, offset)));
            }
        }
        return best;
    }

    private static List<float[]> rotate(List<float[]> points, int offset) {
        List<float[]> rotated = new ArrayList<>(points.size());
        for (int i = 0; i < points.size(); i++) rotated.add(points.get((i + offset) % points.size()));
        return rotated;
    }

    private static float averageDistance(List<float[]> a, List<float[]> b) {
        float total = 0.0F;
        for (int i = 0; i < a.size(); i++) total += distance(a.get(i), b.get(i));
        return total / a.size();
    }

    private static Template template(ConstructTool tool, boolean closed, float... coordinates) {
        List<float[]> points = new ArrayList<>();
        for (int i = 0; i < coordinates.length; i += 2) points.add(new float[]{coordinates[i], coordinates[i + 1]});
        return new Template(tool, normalize(resample(points)), closed);
    }

    private static Template arc(ConstructTool tool, float fromDegrees, float toDegrees) {
        List<float[]> points = new ArrayList<>();
        for (int i = 0; i <= POINTS; i++) {
            float angle = (fromDegrees + (toDegrees - fromDegrees) * i / POINTS) * Mth.DEG_TO_RAD;
            points.add(new float[]{Mth.cos(angle), Mth.sin(angle)});
        }
        return new Template(tool, normalize(resample(points)), false);
    }

    private static Template circle(ConstructTool tool) {
        List<float[]> points = new ArrayList<>();
        for (int i = 0; i <= POINTS; i++) {
            float angle = Mth.HALF_PI - i * Mth.TWO_PI / POINTS;
            points.add(new float[]{Mth.cos(angle), Mth.sin(angle)});
        }
        return new Template(tool, normalize(resample(points)), true);
    }

    private static List<float[]> resample(List<float[]> points) {
        float interval = length(points) / (POINTS - 1);
        List<float[]> source = new ArrayList<>(points);
        List<float[]> result = new ArrayList<>(POINTS);
        result.add(source.getFirst());

        float accumulated = 0.0F;
        for (int i = 1; i < source.size(); i++) {
            float[] previous = source.get(i - 1);
            float[] current = source.get(i);
            float segment = distance(previous, current);
            if (interval > 0.0F && accumulated + segment >= interval) {
                float t = (interval - accumulated) / segment;
                float[] point = {previous[0] + t * (current[0] - previous[0]), previous[1] + t * (current[1] - previous[1])};
                result.add(point);
                source.add(i, point);
                accumulated = 0.0F;
            } else {
                accumulated += segment;
            }
        }
        while (result.size() < POINTS) result.add(source.getLast());
        return result.subList(0, POINTS);
    }

    private static List<float[]> normalize(List<float[]> points) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (float[] point : points) {
            minX = Math.min(minX, point[0]);
            minY = Math.min(minY, point[1]);
            maxX = Math.max(maxX, point[0]);
            maxY = Math.max(maxY, point[1]);
        }
        float size = Math.max(Math.max(maxX - minX, maxY - minY), 1.0E-4F);
        float centerX = (minX + maxX) * 0.5F;
        float centerY = (minY + maxY) * 0.5F;

        List<float[]> normalized = new ArrayList<>(points.size());
        for (float[] point : points) normalized.add(new float[]{(point[0] - centerX) / size, (point[1] - centerY) / size});
        return normalized;
    }

    private static float distance(float[] a, float[] b) {
        float dx = a[0] - b[0];
        float dy = a[1] - b[1];
        return Mth.sqrt(dx * dx + dy * dy);
    }

    private GestureRecognizer() {}
}
