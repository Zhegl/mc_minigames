package me.petr1furious.potatominigames.games.party;

/** Difficulty and swept collision rules shared by the live hazards and server-free checks. */
final class RhinoHazards {
    private RhinoHazards() { }
    static int stage(long elapsed) { return (int) Math.min(10, elapsed / (45 * 20)); }
    static int radius(long elapsed) { return elapsed < 45 * 20 ? 18 : (int) Math.max(5, 17 - (elapsed - 45 * 20) / (15 * 20)); }
    static double speed(int stage) { return Math.min(1.2, .55 + stage * .12); }
    static int cooldown(int stage) { return Math.max(35, 160 - stage * 20); }
    static int windup(int stage) { return Math.max(10, 28 - stage * 3); }
    static int mineInterval(int initialSeconds, int stage) { return Math.max(20, initialSeconds * 20 - stage * 10); }
    static int mineBatch(int stage) { return Math.min(3, 1 + stage / 2); }
    static double segmentDistanceSquared(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax, dz = bz - az, length = dx * dx + dz * dz;
        double t = length < 1e-9 ? 0 : Math.clamp(((px - ax) * dx + (pz - az) * dz) / length, 0, 1);
        double x = px - (ax + t * dx), z = pz - (az + t * dz);
        return x * x + z * z;
    }
}
