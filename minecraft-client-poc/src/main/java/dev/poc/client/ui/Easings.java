package dev.poc.client.ui;

/**
 * Easing curves, all mapping {@code [0,1] -> [0,1]}.
 *
 * <p>Pick by intent: {@link #OUT_EXPO} for things entering the screen (fast start, long settle),
 * {@link #IN_OUT_CUBIC} for things moving between two on-screen positions, {@link #OUT_BACK} for
 * the small overshoot that makes a menu feel physical rather than mechanical. Linear is almost
 * always the wrong choice and is offered mainly for progress bars, where honesty beats feel.
 */
@FunctionalInterface
public interface Easings {

    float apply(float t);

    Easings LINEAR = t -> t;

    Easings OUT_CUBIC = t -> {
        float f = 1f - t;
        return 1f - f * f * f;
    };

    Easings IN_OUT_CUBIC = t -> t < 0.5f
            ? 4f * t * t * t
            : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;

    Easings OUT_EXPO = t -> t >= 1f ? 1f : 1f - (float) Math.pow(2, -10 * t);

    Easings OUT_BACK = t -> {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float f = t - 1f;
        return 1f + c3 * f * f * f + c1 * f * f;
    };

    static float clamp01(float value) {
        return value < 0f ? 0f : Math.min(value, 1f);
    }

    static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }
}
