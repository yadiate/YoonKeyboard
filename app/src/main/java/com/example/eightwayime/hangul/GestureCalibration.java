package com.example.eightwayime.hangul;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class GestureCalibration {
    private static final int VERSION = 1;
    private static final int MIN_DIRECTION_SAMPLES = 2;
    private static final int MIN_LONG_SAMPLES = 2;

    private GestureCalibration() {
    }

    public enum DirectionClass {
        RIGHT("R"),
        LEFT("L"),
        UP("U"),
        DOWN("D"),
        TOP_RIGHT("TR"),
        TOP_LEFT("TL"),
        BOTTOM_RIGHT("BR"),
        BOTTOM_LEFT("BL"),
        LONG_HORIZONTAL("LH"),
        LONG_VERTICAL("LV");

        final String code;

        DirectionClass(String code) {
            this.code = code;
        }

        static DirectionClass fromCode(String code) {
            for (DirectionClass value : values()) {
                if (value.code.equals(code)) {
                    return value;
                }
            }
            return null;
        }
    }

    public static DirectionClass classForVowel(int vowelIndex) {
        switch (vowelIndex) {
            case HangulComposer.V_A:
                return DirectionClass.RIGHT;
            case HangulComposer.V_EO:
                return DirectionClass.LEFT;
            case HangulComposer.V_O:
                return DirectionClass.UP;
            case HangulComposer.V_U:
                return DirectionClass.DOWN;
            case HangulComposer.V_YA:
                return DirectionClass.BOTTOM_RIGHT;
            case HangulComposer.V_YEO:
                return DirectionClass.TOP_LEFT;
            case HangulComposer.V_YO:
                return DirectionClass.TOP_RIGHT;
            case HangulComposer.V_YU:
                return DirectionClass.BOTTOM_LEFT;
            case HangulComposer.V_EU:
                return DirectionClass.LONG_HORIZONTAL;
            case HangulComposer.V_I:
                return DirectionClass.LONG_VERTICAL;
            default:
                return null;
        }
    }

    public static boolean isHangulSyllable(char value) {
        return value >= 0xAC00 && value <= 0xD7A3;
    }

    public static int leadingIndex(char syllable) {
        int offset = syllable - 0xAC00;
        return offset / (21 * 28);
    }

    public static int vowelIndex(char syllable) {
        int offset = syllable - 0xAC00;
        return (offset % (21 * 28)) / 28;
    }

    public static Profile train(List<Sample> samples, float fallbackShortMm, float fallbackLongMm) {
        Profile profile = new Profile();
        profile.totalSamples = samples.size();
        profile.global = buildProfile(samples, fallbackShortMm, fallbackLongMm);

        Map<String, List<Sample>> byConsonant = new HashMap<>();
        for (Sample sample : samples) {
            List<Sample> list = byConsonant.get(sample.consonantLabel);
            if (list == null) {
                list = new ArrayList<>();
                byConsonant.put(sample.consonantLabel, list);
            }
            list.add(sample);
        }
        for (Map.Entry<String, List<Sample>> entry : byConsonant.entrySet()) {
            profile.consonants.put(entry.getKey(), buildProfile(entry.getValue(), fallbackShortMm, fallbackLongMm));
        }
        return profile;
    }

    public static int sampleCountFromJson(String json) {
        return Profile.fromJson(json).totalSamples();
    }

    public static String samplesToJson(List<Sample> samples) {
        JSONArray array = new JSONArray();
        for (Sample sample : samples) {
            array.put(sample.toJson());
        }
        return array.toString();
    }

    public static List<Sample> samplesFromJson(String json) {
        List<Sample> samples = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) {
            return samples;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                Sample sample = Sample.fromJson(array.optJSONObject(i));
                if (sample != null) {
                    samples.add(sample);
                }
            }
        } catch (JSONException ignored) {
            return new ArrayList<>();
        }
        return samples;
    }

    private static ConsonantProfile buildProfile(List<Sample> samples, float fallbackShortMm, float fallbackLongMm) {
        ConsonantProfile profile = new ConsonantProfile();
        profile.totalSamples = samples.size();
        for (DirectionClass directionClass : DirectionClass.values()) {
            List<Sample> directionSamples = filter(samples, directionClass);
            if (isDiagonal(directionClass) && directionSamples.size() >= MIN_DIRECTION_SAMPLES) {
                profile.directions.put(directionClass, buildDirectionProfile(directionSamples, fallbackShortMm, fallbackLongMm));
            }
        }

        List<Float> shortHorizontal = distances(samples, DirectionClass.RIGHT, DirectionClass.LEFT);
        List<Float> shortVertical = distances(samples, DirectionClass.UP, DirectionClass.DOWN);
        List<Float> longHorizontal = distances(samples, DirectionClass.LONG_HORIZONTAL);
        List<Float> longVertical = distances(samples, DirectionClass.LONG_VERTICAL);

        if (longHorizontal.size() >= MIN_LONG_SAMPLES) {
            profile.longHorizontalMm = longThreshold(shortHorizontal, longHorizontal, fallbackLongMm);
            profile.longHorizontalSamples = longHorizontal.size();
        }
        if (longVertical.size() >= MIN_LONG_SAMPLES) {
            profile.longVerticalMm = longThreshold(shortVertical, longVertical, fallbackLongMm);
            profile.longVerticalSamples = longVertical.size();
        }
        return profile;
    }

    private static DirectionProfile buildDirectionProfile(List<Sample> samples, float fallbackShortMm,
                                                          float fallbackLongMm) {
        float center = circularMean(samples);
        List<Float> angleDiffs = new ArrayList<>();
        List<Float> distances = new ArrayList<>();
        for (Sample sample : samples) {
            angleDiffs.add(Math.abs(angleDiff(sample.angleDegrees, center)));
            distances.add(sample.distanceMm);
        }
        float mad = median(angleDiffs);
        float tolerance = clamp(mad * 2.4f + 10f, 18f, 34f);
        float minDistance = clamp(median(distances) * 0.45f, fallbackShortMm * 0.55f, fallbackLongMm * 0.9f);
        return new DirectionProfile(center, tolerance, minDistance, samples.size());
    }

    private static float longThreshold(List<Float> shortDistances, List<Float> longDistances, float fallbackLongMm) {
        float longMedian = median(longDistances);
        float threshold;
        if (shortDistances.size() >= 2) {
            threshold = (median(shortDistances) + longMedian) / 2f;
        } else {
            threshold = longMedian * 0.72f;
        }
        return clamp(threshold, fallbackLongMm * 0.55f, fallbackLongMm * 2.0f);
    }

    private static List<Sample> filter(List<Sample> samples, DirectionClass directionClass) {
        List<Sample> filtered = new ArrayList<>();
        for (Sample sample : samples) {
            if (sample.directionClass == directionClass) {
                filtered.add(sample);
            }
        }
        return filtered;
    }

    private static List<Float> distances(List<Sample> samples, DirectionClass... classes) {
        List<Float> values = new ArrayList<>();
        for (Sample sample : samples) {
            for (DirectionClass directionClass : classes) {
                if (sample.directionClass == directionClass) {
                    values.add(sample.distanceMm);
                    break;
                }
            }
        }
        return values;
    }

    private static boolean isDiagonal(DirectionClass directionClass) {
        return directionClass == DirectionClass.TOP_RIGHT
                || directionClass == DirectionClass.TOP_LEFT
                || directionClass == DirectionClass.BOTTOM_RIGHT
                || directionClass == DirectionClass.BOTTOM_LEFT;
    }

    private static float circularMean(List<Sample> samples) {
        double sin = 0d;
        double cos = 0d;
        for (Sample sample : samples) {
            double radians = Math.toRadians(sample.angleDegrees);
            sin += Math.sin(radians);
            cos += Math.cos(radians);
        }
        return normalizeAngle((float) Math.toDegrees(Math.atan2(sin, cos)));
    }

    public static float angleDiff(float first, float second) {
        float diff = normalizeAngle(first - second);
        if (diff > 180f) {
            diff -= 360f;
        }
        return diff;
    }

    private static float normalizeAngle(float angle) {
        float normalized = angle % 360f;
        if (normalized < -180f) {
            normalized += 360f;
        } else if (normalized > 180f) {
            normalized -= 360f;
        }
        return normalized;
    }

    private static float median(List<Float> values) {
        if (values.isEmpty()) {
            return 0f;
        }
        List<Float> sorted = new ArrayList<>(values);
        sorted.sort(Float::compare);
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }
        return (sorted.get(middle - 1) + sorted.get(middle)) / 2f;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }

    public static class Sample {
        public final String consonantLabel;
        public final int expectedVowel;
        public final DirectionClass directionClass;
        public final float angleDegrees;
        public final float distanceMm;
        public final float dxMm;
        public final float dyMm;
        public final float pathMm;
        public final long durationMs;

        public Sample(Consonant consonant, int expectedVowel, DirectionClass directionClass,
                      GestureVowelMapper.Trace trace) {
            this(consonant.label(), expectedVowel, directionClass, trace.angleDegrees, trace.distanceMm,
                    trace.dxMm, trace.dyMm, trace.pathMm, trace.durationMs);
        }

        Sample(String consonantLabel, int expectedVowel, DirectionClass directionClass, float angleDegrees,
               float distanceMm, float dxMm, float dyMm, float pathMm, long durationMs) {
            this.consonantLabel = consonantLabel;
            this.expectedVowel = expectedVowel;
            this.directionClass = directionClass;
            this.angleDegrees = angleDegrees;
            this.distanceMm = distanceMm;
            this.dxMm = dxMm;
            this.dyMm = dyMm;
            this.pathMm = pathMm;
            this.durationMs = durationMs;
        }

        JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put("consonant", consonantLabel);
                object.put("vowel", expectedVowel);
                object.put("direction", directionClass.code);
                object.put("angle", angleDegrees);
                object.put("distance", distanceMm);
                object.put("dx", dxMm);
                object.put("dy", dyMm);
                object.put("path", pathMm);
                object.put("duration", durationMs);
            } catch (JSONException ignored) {
            }
            return object;
        }

        static Sample fromJson(JSONObject object) {
            if (object == null) {
                return null;
            }
            DirectionClass directionClass = DirectionClass.fromCode(object.optString("direction", ""));
            if (directionClass == null) {
                return null;
            }
            String consonantLabel = object.optString("consonant", "");
            if (consonantLabel.isEmpty()) {
                return null;
            }
            return new Sample(
                    consonantLabel,
                    object.optInt("vowel", -1),
                    directionClass,
                    (float) object.optDouble("angle", 0d),
                    (float) object.optDouble("distance", 0d),
                    (float) object.optDouble("dx", 0d),
                    (float) object.optDouble("dy", 0d),
                    (float) object.optDouble("path", 0d),
                    object.optLong("duration", 0L));
        }
    }

    public static class DirectionProfile {
        public final float centerAngle;
        public final float tolerance;
        public final float minDistanceMm;
        public final int samples;

        DirectionProfile(float centerAngle, float tolerance, float minDistanceMm, int samples) {
            this.centerAngle = centerAngle;
            this.tolerance = tolerance;
            this.minDistanceMm = minDistanceMm;
            this.samples = samples;
        }

        JSONObject toJson() throws JSONException {
            JSONObject object = new JSONObject();
            object.put("angle", centerAngle);
            object.put("tolerance", tolerance);
            object.put("minDistance", minDistanceMm);
            object.put("samples", samples);
            return object;
        }

        static DirectionProfile fromJson(JSONObject object) {
            if (object == null) {
                return null;
            }
            return new DirectionProfile(
                    (float) object.optDouble("angle", 0d),
                    (float) object.optDouble("tolerance", 0d),
                    (float) object.optDouble("minDistance", 0d),
                    object.optInt("samples", 0));
        }
    }

    public static class ConsonantProfile {
        private final EnumMap<DirectionClass, DirectionProfile> directions =
                new EnumMap<>(DirectionClass.class);
        private int totalSamples;
        private float longHorizontalMm;
        private int longHorizontalSamples;
        private float longVerticalMm;
        private int longVerticalSamples;

        public int totalSamples() {
            return totalSamples;
        }

        JSONObject toJson() throws JSONException {
            JSONObject object = new JSONObject();
            object.put("samples", totalSamples);
            JSONObject directionsObject = new JSONObject();
            for (Map.Entry<DirectionClass, DirectionProfile> entry : directions.entrySet()) {
                directionsObject.put(entry.getKey().code, entry.getValue().toJson());
            }
            object.put("directions", directionsObject);
            if (longHorizontalSamples > 0) {
                object.put("longH", longHorizontalMm);
                object.put("longHSamples", longHorizontalSamples);
            }
            if (longVerticalSamples > 0) {
                object.put("longV", longVerticalMm);
                object.put("longVSamples", longVerticalSamples);
            }
            return object;
        }

        static ConsonantProfile fromJson(JSONObject object) {
            ConsonantProfile profile = new ConsonantProfile();
            if (object == null) {
                return profile;
            }
            profile.totalSamples = object.optInt("samples", 0);
            JSONObject directionsObject = object.optJSONObject("directions");
            if (directionsObject != null) {
                Iterator<String> keys = directionsObject.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    DirectionClass directionClass = DirectionClass.fromCode(key);
                    DirectionProfile directionProfile = DirectionProfile.fromJson(directionsObject.optJSONObject(key));
                    if (directionClass != null && directionProfile != null) {
                        profile.directions.put(directionClass, directionProfile);
                    }
                }
            }
            profile.longHorizontalMm = (float) object.optDouble("longH", 0d);
            profile.longHorizontalSamples = object.optInt("longHSamples", 0);
            profile.longVerticalMm = (float) object.optDouble("longV", 0d);
            profile.longVerticalSamples = object.optInt("longVSamples", 0);
            return profile;
        }
    }

    public static class Profile {
        private final Map<String, ConsonantProfile> consonants = new HashMap<>();
        private ConsonantProfile global = new ConsonantProfile();
        private int totalSamples;

        public int totalSamples() {
            return totalSamples;
        }

        public boolean hasData() {
            return totalSamples > 0 || !consonants.isEmpty() || profileHasDirections(global);
        }

        public int consonantSamples(Consonant consonant) {
            ConsonantProfile consonantProfile = consonant == null ? null : consonants.get(consonant.label());
            return consonantProfile == null ? 0 : consonantProfile.totalSamples();
        }

        public void setDirectionAngle(Consonant consonant, DirectionClass directionClass, float angleDegrees,
                                      float fallbackShortMm, float fallbackLongMm) {
            if (consonant == null || directionClass == null || !isDiagonal(directionClass)) {
                return;
            }
            ConsonantProfile consonantProfile = consonants.get(consonant.label());
            if (consonantProfile == null) {
                consonantProfile = new ConsonantProfile();
                consonants.put(consonant.label(), consonantProfile);
            }
            DirectionProfile previous = consonantProfile.directions.get(directionClass);
            float tolerance = previous == null ? 28f : previous.tolerance;
            float minDistance = previous == null
                    ? Math.max(1f, Math.min(fallbackShortMm * 0.85f, fallbackLongMm * 0.45f))
                    : previous.minDistanceMm;
            int samples = previous == null ? MIN_DIRECTION_SAMPLES : Math.max(previous.samples, MIN_DIRECTION_SAMPLES);
            consonantProfile.directions.put(directionClass,
                    new DirectionProfile(normalizeAngle(angleDegrees), tolerance, minDistance, samples));
            consonantProfile.totalSamples = Math.max(consonantProfile.totalSamples, 1);
        }

        private boolean profileHasDirections(ConsonantProfile profile) {
            return profile != null && !profile.directions.isEmpty();
        }

        public DirectionProfile directionProfile(Consonant consonant, DirectionClass directionClass) {
            ConsonantProfile consonantProfile = consonant == null ? null : consonants.get(consonant.label());
            DirectionProfile directionProfile = profileDirection(consonantProfile, directionClass);
            if (directionProfile != null) {
                return directionProfile;
            }
            return profileDirection(global, directionClass);
        }

        public float longHorizontalMm(Consonant consonant, float fallbackMm) {
            ConsonantProfile consonantProfile = consonant == null ? null : consonants.get(consonant.label());
            if (consonantProfile != null && consonantProfile.longHorizontalSamples >= MIN_LONG_SAMPLES) {
                return consonantProfile.longHorizontalMm;
            }
            if (global != null && global.longHorizontalSamples >= MIN_LONG_SAMPLES) {
                return global.longHorizontalMm;
            }
            return fallbackMm;
        }

        public float longVerticalMm(Consonant consonant, float fallbackMm) {
            ConsonantProfile consonantProfile = consonant == null ? null : consonants.get(consonant.label());
            if (consonantProfile != null && consonantProfile.longVerticalSamples >= MIN_LONG_SAMPLES) {
                return consonantProfile.longVerticalMm;
            }
            if (global != null && global.longVerticalSamples >= MIN_LONG_SAMPLES) {
                return global.longVerticalMm;
            }
            return fallbackMm;
        }

        public String toJson() {
            try {
                JSONObject object = new JSONObject();
                object.put("version", VERSION);
                object.put("samples", totalSamples);
                object.put("global", global.toJson());
                JSONObject consonantsObject = new JSONObject();
                for (Map.Entry<String, ConsonantProfile> entry : consonants.entrySet()) {
                    consonantsObject.put(entry.getKey(), entry.getValue().toJson());
                }
                object.put("consonants", consonantsObject);
                return object.toString();
            } catch (JSONException e) {
                return "";
            }
        }

        public static Profile fromJson(String json) {
            Profile profile = new Profile();
            if (json == null || json.trim().isEmpty()) {
                return profile;
            }
            try {
                JSONObject object = new JSONObject(json);
                profile.totalSamples = object.optInt("samples", 0);
                profile.global = ConsonantProfile.fromJson(object.optJSONObject("global"));
                JSONObject consonantsObject = object.optJSONObject("consonants");
                if (consonantsObject != null) {
                    Iterator<String> keys = consonantsObject.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        profile.consonants.put(key, ConsonantProfile.fromJson(consonantsObject.optJSONObject(key)));
                    }
                }
            } catch (JSONException ignored) {
                return new Profile();
            }
            return profile;
        }

        private DirectionProfile profileDirection(ConsonantProfile profile, DirectionClass directionClass) {
            if (profile == null) {
                return null;
            }
            DirectionProfile directionProfile = profile.directions.get(directionClass);
            if (directionProfile == null || directionProfile.samples < MIN_DIRECTION_SAMPLES) {
                return null;
            }
            return directionProfile;
        }
    }
}
