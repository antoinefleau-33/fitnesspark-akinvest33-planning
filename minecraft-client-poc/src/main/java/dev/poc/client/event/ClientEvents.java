package dev.poc.client.event;

/** The handful of core events the demo bootstrap emits. Real clients grow this a lot. */
public final class ClientEvents {

    private ClientEvents() {
    }

    /** Fired once per client tick (20 Hz), before world logic. */
    public record Tick(long tickCount) {
    }

    /** Fired once per rendered frame. {@code partialTicks} is the interpolation factor. */
    public record RenderFrame(float partialTicks, float deltaSeconds) {
    }

    /** Fired when the active Minecraft version runtime has finished swapping. */
    public record VersionChanged(String previousVersionId, String newVersionId) {
    }
}
