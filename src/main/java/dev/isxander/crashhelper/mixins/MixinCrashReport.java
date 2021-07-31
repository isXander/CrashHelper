package dev.isxander.crashhelper.mixins;

import dev.isxander.crashhelper.CrashHelper;
import net.minecraft.crash.CrashReport;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = CrashReport.class, priority = 5000)
public class MixinCrashReport {

    @Shadow @Final private String description;

    @Redirect(method = "getCompleteReport", at = @At(value = "INVOKE", target = "Ljava/lang/StringBuilder;toString()Ljava/lang/String;", remap = false))
    private String modifyDescription(StringBuilder sb) {
        String str = sb.toString();
        if (description.equals("Loading screen debug info")) return str;

        return str.replace(this.description, CrashHelper.scanReport(str, this.description));
    }


}
