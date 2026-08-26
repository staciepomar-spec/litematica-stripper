package net.isswr.blockfilter.mixin;

import fi.dy.masa.litematica.world.WorldSchematic;
import net.isswr.blockfilter.BlockFilterManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "me.aleksilassila.litematica.printer.printer.PlacementGuide")
public class PrinterPlacementGuideMixin
{
    @Inject(method = "buildAction", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void blockfilter$skipHiddenBlock(World world, WorldSchematic worldSchematic, BlockPos pos,
                                             @Coerce Object requiredType,
                                             CallbackInfoReturnable<Object> cir)
    {
        if (worldSchematic != null && BlockFilterManager.shouldHide(worldSchematic.getBlockState(pos)))
        {
            cir.setReturnValue(null);
        }
    }
}
