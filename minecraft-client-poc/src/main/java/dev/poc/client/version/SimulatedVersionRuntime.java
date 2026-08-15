package dev.poc.client.version;

/**
 * Stand-in runtime for the demo and for tests: it moves through the same phases with the same
 * threading contract, without a Minecraft jar. Useful for exercising the coordinator, the swap
 * hooks and the UI progress bar on a machine that has no game installed.
 */
public final class SimulatedVersionRuntime implements VersionRuntime {

    private final String versionId;
    private final long prepareMillis;
    private volatile Phase phase = Phase.NEW;

    public SimulatedVersionRuntime(String versionId, long prepareMillis) {
        this.versionId = versionId;
        this.prepareMillis = prepareMillis;
    }

    @Override
    public String versionId() {
        return versionId;
    }

    @Override
    public Phase phase() {
        return phase;
    }

    @Override
    public void prepare() throws InterruptedException {
        if (phase == Phase.STANDBY) {
            return;
        }
        phase = Phase.PREPARING;
        Thread.sleep(prepareMillis);
        phase = Phase.STANDBY;
    }

    @Override
    public void attach(long windowHandle) {
        phase = Phase.ACTIVE;
    }

    @Override
    public void detach() {
        phase = Phase.DETACHED;
    }

    @Override
    public void close() {
        phase = Phase.CLOSED;
    }
}
