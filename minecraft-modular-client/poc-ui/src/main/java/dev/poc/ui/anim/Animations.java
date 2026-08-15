package dev.poc.ui.anim;

/**
 * Système d'animation.
 *
 * <p>Deux modèles cohabitent, et le choix entre les deux n'est pas cosmétique :
 * <ul>
 *   <li><b>Courbes de durée</b> ({@link Tween}) pour les transitions à début et fin connus :
 *       apparition d'un écran, fondu d'un overlay. Prédictible, chorégraphiable.</li>
 *   <li><b>Ressorts</b> ({@link Spring}) pour tout ce qui réagit à l'utilisateur : survol,
 *       enfoncement, glissement. Un ressort accepte un changement de cible <em>en cours de
 *       course</em> en conservant sa vélocité. Une courbe de durée, elle, redémarre à zéro : c'est
 *       exactement ce qui donne cette sensation de saccade quand on balaie rapidement une liste de
 *       boutons. Aucun réglage de courbe ne corrige ça — il faut changer de modèle.</li>
 * </ul>
 */
public final class Animations {

    private Animations() {}

    /**
     * Courbe d'interpolation. Interface dédiée plutôt que {@code DoubleUnaryOperator} : toute la
     * chaîne d'animation est en {@code float} (c'est ce qu'attendent les uniformes GL), et passer
     * par des doubles imposerait un transtypage à chaque frame pour aucun gain de précision.
     */
    @FunctionalInterface
    public interface Curve {
        float apply(float t);
    }

    /** Interpolations classiques, normalisées sur [0,1]. */
    public static final class Easing {
        private Easing() {}

        public static float linear(float t) { return t; }

        public static float easeOutCubic(float t) {
            float f = t - 1f;
            return f * f * f + 1f;
        }

        public static float easeInOutCubic(float t) {
            return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
        }

        /** Léger dépassement — donne du « poids » aux apparitions de panneaux. */
        public static float easeOutBack(float t) {
            final float c1 = 1.70158f;
            final float c3 = c1 + 1f;
            float f = t - 1f;
            return 1f + c3 * f * f * f + c1 * f * f;
        }

        public static float easeOutExpo(float t) {
            return t >= 1f ? 1f : 1f - (float) Math.pow(2, -10 * t);
        }
    }

    /** Interpolation à durée fixe. */
    public static final class Tween {
        private float from, to, value;
        private float elapsed, duration;
        private Curve curve = Easing::easeOutCubic;

        public Tween(float initial) { this.value = this.from = this.to = initial; }

        public Tween curve(Curve c) { this.curve = c; return this; }

        public void to(float target, float durationSeconds) {
            if (Math.abs(target - to) < 1e-5f) return;
            this.from = value;
            this.to = target;
            this.duration = Math.max(durationSeconds, 1e-4f);
            this.elapsed = 0f;
        }

        public void update(float dt) {
            if (elapsed >= duration) { value = to; return; }
            elapsed = Math.min(elapsed + dt, duration);
            float t = curve.apply(elapsed / duration);
            value = from + (to - from) * t;
        }

        public float value() { return value; }
        public boolean settled() { return elapsed >= duration; }
    }

    /**
     * Oscillateur harmonique amorti, intégré en semi-implicite (Euler symplectique).
     *
     * <p>Le semi-implicite — vélocité mise à jour <em>avant</em> la position — est stable pour des
     * raideurs élevées là où l'Euler explicite diverge et fait exploser l'animation à bas
     * framerate. Sur un client qui peut chuter de 240 à 20 fps pendant un chargement de chunks,
     * ce n'est pas théorique.
     *
     * <p>Le pas est en outre sous-divisé : un {@code dt} de 200 ms (fenêtre déplacée, GC long)
     * ferait diverger n'importe quel intégrateur explicite.
     */
    public static final class Spring {
        private float value, target, velocity;
        private final float stiffness, damping;

        /**
         * @param stiffness raideur ; 170 ≈ réactif, 100 ≈ doux
         * @param damping   amortissement ; {@code 2*sqrt(stiffness)} = critique (pas de rebond)
         */
        public Spring(float initial, float stiffness, float damping) {
            this.value = this.target = initial;
            this.stiffness = stiffness;
            this.damping = damping;
        }

        /** Réglage par défaut : réactif, très léger rebond. */
        public static Spring snappy(float initial) { return new Spring(initial, 210f, 26f); }

        /** Amorti critique : rejoint la cible au plus vite sans jamais la dépasser. */
        public static Spring smooth(float initial) {
            return new Spring(initial, 120f, 2f * (float) Math.sqrt(120f));
        }

        public void target(float t) { this.target = t; }

        public void snapTo(float v) { this.value = this.target = v; this.velocity = 0f; }

        public void update(float dt) {
            final float maxStep = 1f / 120f;
            int steps = Math.min(16, Math.max(1, (int) Math.ceil(dt / maxStep)));
            float h = dt / steps;
            for (int i = 0; i < steps; i++) {
                float accel = stiffness * (target - value) - damping * velocity;
                velocity += accel * h;      // vélocité d'abord
                value += velocity * h;      // puis position : c'est ce qui rend le schéma stable
            }
            if (Math.abs(target - value) < 1e-4f && Math.abs(velocity) < 1e-3f) {
                value = target;
                velocity = 0f;
            }
        }

        public float value() { return value; }
        public boolean settled() { return value == target && velocity == 0f; }
    }

    /** Décalage en cascade pour l'entrée d'une liste : chaque élément part un cran plus tard. */
    public static float staggerDelay(int index, float perItemSeconds, float maxSeconds) {
        return Math.min(index * perItemSeconds, maxSeconds);
    }

    public static int lerpColor(int a, int b, float t) {
        t = Math.clamp(t, 0f, 1f);
        int aa = (a >>> 24) & 0xFF, ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24)
                | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8)
                | Math.round(ab + (bb - ab) * t);
    }
}
