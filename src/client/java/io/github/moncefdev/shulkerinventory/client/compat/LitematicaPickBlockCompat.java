package io.github.moncefdev.shulkerinventory.client.compat;

import fi.dy.masa.litematica.interfaces.ISchematicPickBlockEventListener;
import fi.dy.masa.litematica.schematic.pickblock.SchematicPickBlockEventHandler;
import fi.dy.masa.litematica.schematic.pickblock.SchematicPickBlockEventResult;
import io.github.moncefdev.shulkerinventory.ShulkerContents;
import io.github.moncefdev.shulkerinventory.ShulkerInventory;
import io.github.moncefdev.shulkerinventory.client.PocketBuildClient;
import io.github.moncefdev.shulkerinventory.client.PocketBuildMode;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.lang.reflect.Field;
import java.util.function.Supplier;

// Optional Litematica interop, loaded ONLY when Litematica is present (isModLoaded gate in the Fabric client
// entrypoint), so its fi.dy.masa.litematica.* references are never linked without the mod. Middle-clicking a
// schematic ghost block in Pocket-Build routes through the SAME handlePocketBuildPick as the vanilla pick;
// Litematica consumes that click at malilib's mouse layer, so its SchematicPickBlock event is the only hook.
// Includes a reflection workaround for Litematica's sticky processingCancelled flag, applied only when WE were
// the canceller; drop resetProcessingCancelled once the upstream fix ships. TECHNICAL.md section 12.
public final class LitematicaPickBlockCompat implements ISchematicPickBlockEventListener {

    private static final String LISTENER_NAME = "builders-shulkers";
    private static Field processingCancelledField;

    public static void register() {
        SchematicPickBlockEventHandler.getInstance()
                .registerSchematicPickBlockEventListener(new LitematicaPickBlockCompat());
    }

    @Override
    public Supplier<String> getName() {
        return () -> LISTENER_NAME;
    }

    @Override
    public SchematicPickBlockEventResult onSchematicPickBlockPrePick(Level schematicWorld, BlockPos pos,
            BlockState expectedState, ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (!PocketBuildMode.isActive() || mc.player == null
                || !ShulkerContents.isShulker(mc.player.getMainHandItem())) {
            return SchematicPickBlockEventResult.SUCCESS;
        }
        // selected -> the block was in the held shulker: CONSUME the pick (no swap, stay in mode). Otherwise let
        // Litematica pick normally; if the pick would take an item, handlePocketBuildPick has already EXITED the mode
        // (mirroring the vanilla pick), so Litematica's hotbar swap happens with Pocket-Build cleanly closed.
        boolean selected = PocketBuildClient.handlePocketBuildPick(mc.player, stack);
        return selected ? SchematicPickBlockEventResult.CANCEL : SchematicPickBlockEventResult.SUCCESS;
    }

    @Override
    public SchematicPickBlockEventResult onSchematicPickBlockStart(boolean closest) {
        return SchematicPickBlockEventResult.SUCCESS;
    }

    @Override
    public SchematicPickBlockEventResult onSchematicPickBlockPreGather(Level schematicWorld, BlockPos pos,
            BlockState expectedState) {
        return SchematicPickBlockEventResult.SUCCESS;
    }

    @Override
    public void onSchematicPickBlockSuccess() {
        // no-op
    }

    @Override
    public void onSchematicPickBlockCancelled(Supplier<String> cancelledBy) {
        // Only un-stick the flag if WE were the canceller (the upstream no-reset bug). Never touch another mod's cancel;
        // becomes a no-op once Litematica resets the flag itself.
        if (cancelledBy != null && LISTENER_NAME.equals(cancelledBy.get())) {
            resetProcessingCancelled();
        }
    }

    // Reflection workaround for the upstream sticky-flag bug: set SchematicPickBlockEventHandler.processingCancelled back
    // to false (there is no public reset). Remove once Litematica resets the flag itself.
    private static void resetProcessingCancelled() {
        try {
            if (processingCancelledField == null) {
                processingCancelledField = SchematicPickBlockEventHandler.class.getDeclaredField("processingCancelled");
                processingCancelledField.setAccessible(true);
            }
            processingCancelledField.setBoolean(SchematicPickBlockEventHandler.getInstance(), false);
        } catch (Throwable t) {
            ShulkerInventory.LOGGER.warn("[builders-shulkers] could not reset Litematica processingCancelled", t);
        }
    }
}
