package dev.poc.bediag;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Collecte les BlockEntity chargées côté client.
 *
 * <p><b>Seule classe de ce paquet à dépendre de Minecraft.</b> Tout le reste — filtrage,
 * agrégation, politique d'occlusion — est en Java pur et testé séparément. Quand Mojang casse une
 * signature, c'est ici, et nulle part ailleurs, qu'il faut corriger.
 *
 * <h2>Pourquoi passer par les chunks</h2>
 * Il n'existe pas de liste globale des BlockEntity côté client. L'alternative, appeler
 * {@code world.getChunk(x, z)} sur toute la grille de distance de rendu, force le chargement de
 * chunks vides et fausse précisément la mesure qu'on cherche à prendre.
 */
public final class BeCollector {

    private BeCollector() {}

    /**
     * @param maxDistance rayon en blocs ; borne le coût de la collecte elle-même
     */
    public static List<BeSnapshot> collect(MinecraftClient client, double maxDistance) {
        List<BeSnapshot> out = new ArrayList<>(512);
        ClientWorld world = client.world;
        if (world == null) return out;

        BlockEntityRenderDispatcher dispatcher = client.getBlockEntityRenderDispatcher();
        Vec3d camera = client.gameRenderer.getCamera().getPos();
        double maxSq = maxDistance * maxDistance;

        int radius = Math.max(1, client.options.getViewDistance().getValue());
        int centerX = ((int) camera.x) >> 4;
        int centerZ = ((int) camera.z) >> 4;

        for (int cx = centerX - radius; cx <= centerX + radius; cx++) {
            for (int cz = centerZ - radius; cz <= centerZ + radius; cz++) {
                // getChunk(..., false) : ne déclenche PAS de génération. Passer true chargerait
                // des chunks pour les mesurer, ce qui reviendrait à mesurer l'outil lui-même.
                WorldChunk chunk = world.getChunkManager().getWorldChunk(cx, cz, false);
                if (chunk == null) continue;

                for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                    BlockPos pos = entry.getKey();
                    BlockEntity be = entry.getValue();

                    double dx = pos.getX() + 0.5 - camera.x;
                    double dy = pos.getY() + 0.5 - camera.y;
                    double dz = pos.getZ() + 0.5 - camera.z;
                    double distSq = dx * dx + dy * dy + dz * dz;
                    if (distSq > maxSq) continue;

                    BlockEntityRenderer<BlockEntity> renderer = dispatcher.get(be);
                    boolean hasRenderer = renderer != null;
                    // shouldRender() intègre getRenderDistance() ET le test propre au renderer :
                    // un panneau se cull bien plus tôt qu'un coffre. C'est le vrai prédicat.
                    boolean inRange = hasRenderer && renderer.isInRenderDistance(be, camera);

                    out.add(new BeSnapshot(
                            pos.getX(), pos.getY(), pos.getZ(),
                            Registries.BLOCK_ENTITY_TYPE.getId(be.getType()).toString(),
                            hasRenderer,
                            hasClientTicker(world, be),
                            inRange,
                            cx, cz,
                            distSq));
                }
            }
        }
        return out;
    }

    /**
     * Le bloc fournit-il un ticker <b>côté client</b> ?
     *
     * <p>Distinction importante : beaucoup de BlockEntity ont un ticker serveur et aucun client.
     * Les confondre surestime massivement la charge client — les hoppers en sont l'exemple type.
     */
    private static boolean hasClientTicker(ClientWorld world, BlockEntity be) {
        try {
            var state = be.getCachedState();
            if (!(state.getBlock() instanceof BlockEntityProvider provider)) return false;
            return provider.getTicker(world, state, be.getType()) != null;
        } catch (Throwable ignored) {
            // Un mod tiers peut lever depuis getTicker : le diagnostic ne doit pas planter pour ça.
            return false;
        }
    }
}
