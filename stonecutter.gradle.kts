plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.2-fabric" /* [SC] DO NOT EDIT */

// The source is kept in its 26.2 form (the newest target). For older targets, these replacements transform it
// back down. 26.2 split net.minecraft.client.gui.Gui into Gui + a new Hud class (HUD rendering moved to Hud),
// changed screen access (Minecraft.screen field -> Gui.screen() getter, setScreen -> gui.setScreen), and dropped
// two boolean flags from OrderedSubmitNodeCollector.submitModelPart. Each replacement is applied only when building
// a version below 26.2 (i.e. 26.1.2).
stonecutter parameters {
    replacements {
        string(current.parsed < "26.2") { replace("mc.gui.screen()", "mc.screen") }
        string(current.parsed < "26.2") { replace("mc.gui.setScreen(", "mc.setScreen(") }
        string(current.parsed < "26.2") { replace("context.client().gui.setScreen(", "context.client().setScreen(") }
        string(current.parsed < "26.2") { replace("@Mixin(Hud.class)", "@Mixin(Gui.class)") }
        string(current.parsed < "26.2") { replace("import net.minecraft.client.gui.Hud;", "import net.minecraft.client.gui.Gui;") }
        string(current.parsed < "26.2") { replace("overlayCoords, atlasSprite,", "overlayCoords, atlasSprite, false, false,") }
    }
}
