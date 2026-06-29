plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.21.11" /* [SC] DO NOT EDIT */

// The source is kept in its 1.21.11 form. 1.21.10 differs from 1.21.11 by two pure class relocations (handled by the
// string swaps below) plus a structural game-rule API change (handled inline in ModGameRules / ShulkerInventory):
//   - Mojang renamed net.minecraft.resources.ResourceLocation to Identifier in 1.21.11. "Identifier" appears only as
//     that class name in our source (verified), so one swap covers the import, the type uses, and fromNamespaceAndPath.
//   - 1.21.11 split the render-type factories into a new RenderTypes class under .renderer.rendertype; on 1.21.10 they
//     still live on RenderType (.renderer.RenderType), same method names. So: drop the RenderTypes import, move the
//     RenderType import back a package, and rewrite the RenderTypes. / RenderTypes:: factory calls to RenderType.
// Each replacement runs only when building a version below 1.21.11 (i.e. 1.21.10).
stonecutter parameters {
    replacements {
        string(current.parsed < "1.21.11") { replace("Identifier", "ResourceLocation") }
        // Reversible, so the RenderTypes import becomes a comment (not an empty line, which Stonecutter forbids). The
        // factory short-name calls then resolve to the relocated RenderType import below.
        string(current.parsed < "1.21.11") { replace("import net.minecraft.client.renderer.rendertype.RenderTypes;", "// 1.21.10: render-type factories sit on the relocated type imported above") }
        string(current.parsed < "1.21.11") { replace("net.minecraft.client.renderer.rendertype.RenderType;", "net.minecraft.client.renderer.RenderType;") }
        // Same relocation, but in the JVM-descriptor (slash) form used inside a mixin @At target string, which the dotted
        // import swap above does not reach. Without this the lid-fade WrapOperation scans 0 targets on 1.21.10.
        string(current.parsed < "1.21.11") { replace("Lnet/minecraft/client/renderer/rendertype/RenderType;", "Lnet/minecraft/client/renderer/RenderType;") }
        string(current.parsed < "1.21.11") { replace("RenderTypes::", "RenderType::") }
        string(current.parsed < "1.21.11") { replace("RenderTypes.", "RenderType.") }
        // 1.21.11's Minecraft pulls jspecify transitively; 1.21.10's does not. Both bring JetBrains annotations through
        // Fabric Loader, so swap the one @Nullable import to that on 1.21.10 (same usage, no extra dependency).
        string(current.parsed < "1.21.11") { replace("org.jspecify.annotations.Nullable", "org.jetbrains.annotations.Nullable") }
        // 1.21.11's ItemInHandLayer.submitArmWithItem gained a held ItemStack parameter that 1.21.10's lacks; the
        // HeldItemAnimationMixin handler mirrors the target's full argument list (Mixin requires it), so drop that one
        // parameter for 1.21.10. The animation id is read from the render state instead, so the body is unaffected.
        string(current.parsed < "1.21.11") { replace("ItemStackRenderState itemRenderState, ItemStack itemStack, HumanoidArm", "ItemStackRenderState itemRenderState, HumanoidArm") }
    }
}
