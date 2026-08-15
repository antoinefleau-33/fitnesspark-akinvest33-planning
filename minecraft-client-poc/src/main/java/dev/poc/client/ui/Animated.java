package dev.poc.client.ui;

/**
 * A scalar that chases a target over time.
 *
 * <p>Two modes, because UI needs both:
 * <ul>
 *   <li><b>Spring</b> for anything the user can interrupt — hover, selection, drag. A spring has no
 *       fixed duration, so retargeting mid-flight is continuous: move the mouse off a button
 *       halfway through its hover animation and it eases back from where it actually is. A tween
 *       restarts from the current value with a fresh duration and visibly stutters.</li>
 *   <li><b>Tween</b> for scripted, one-shot motion — entrance staggers, screen transitions — where
 *       the exact duration is the point.</li>
 * </ul>
 *
 * <p>The spring integrates with a fixed sub-step. Integrating with the raw frame delta makes
 * stiffness behave differently at 60 and 240 fps, and a long frame (a version swap, a chunk build)
 * can push an explicit integrator unstable enough to fling the value to infinity.
 */
public final class Animated {

    private static final float SUB_STEP = 1f / 240f;
    private static final float MAX_FRAME_TIME = 0.25f;

    private enum Mode {SPRING, TWEEN}

    private final Mode mode;

    private float value;
    private float target;

    // spring
    private float velocity;
    private final float stiffness;
    private final float damping;

    // tween
    private float from;
    private float elapsed;
    private float duration;
    private float delay;
    private final Easings easing;

    private Animated(Mode mode, float initial, float stiffness, float damping,
                     float duration, Easings easing) {
        this.mode = mode;
        this.value = initial;
        this.target = initial;
        this.from = initial;
        this.stiffness = stiffness;
        this.damping = damping;
        this.duration = duration;
        this.easing = easing;
    }

    /** Critically-damped-ish defaults: snappy, no visible bounce. */
    public static Animated spring(float initial) {
        return spring(initial, 220f, 26f);
    }

    public static Animated spring(float initial, float stiffness, float damping) {
        return new Animated(Mode.SPRING, initial, stiffness, damping, 0f, Easings.LINEAR);
    }

    public static Animated tween(float initial, float durationSeconds, Easings easing) {
        return new Animated(Mode.TWEEN, initial, 0f, 0f, durationSeconds, easing);
    }

    /** Delays the start of a tween. Used to stagger a list of widgets into view. */
    public Animated withDelay(float delaySeconds) {
        this.delay = delaySeconds;
        return this;
    }

    public void target(float target) {
        if (this.target == target) {
            return;
        }
        this.target = target;
        if (mode == Mode.TWEEN) {
            this.from = value;
            this.elapsed = 0f;
        }
    }

    /** Jumps to a value with no animation — e.g. when a screen is first shown. */
    public void set(float value) {
        this.value = value;
        this.target = value;
        this.from = value;
        this.velocity = 0f;
        this.elapsed = duration;
    }

    public float value() {
        return value;
    }

    public float target() {
        return target;
    }

    public boolean isSettled() {
        return mode == Mode.SPRING
                ? Math.abs(value - target) < 0.001f && Math.abs(velocity) < 0.001f
                : elapsed >= delay + duration;
    }

    public float update(float deltaSeconds) {
        float dt = Math.min(deltaSeconds, MAX_FRAME_TIME);
        if (mode == Mode.TWEEN) {
            elapsed += dt;
            float progress = duration <= 0f ? 1f
                    : Easings.clamp01((elapsed - delay) / duration);
            value = Easings.lerp(from, target, easing.apply(progress));
            return value;
        }
        float remaining = dt;
        while (remaining > 0f) {
            float step = Math.min(SUB_STEP, remaining);
            float acceleration = -stiffness * (value - target) - damping * velocity;
            velocity += acceleration * step;
            value += velocity * step;
            remaining -= step;
        }
        if (isSettled()) {
            value = target;
            velocity = 0f;
        }
        return value;
    }
}
