package net.isswr.blockfilter.mixin;

import net.isswr.blockfilter.BlockFilterManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "me.aleksilassila.litematica.printer.printer.State")
public class PrinterStateMixin
{
    private static Object printerState;

    @Inject(method = "get", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void blockfilter$treatHiddenAsCorrect(net.minecraft.block.BlockState requiredState,
                                                         net.minecraft.block.BlockState currentState,
                                                         CallbackInfoReturnable<Object> cir)
    {
        if (BlockFilterManager.shouldHide(requiredState))
        {
            cir.setReturnValue(getCorrectState());
        }
    }

    private static Object getCorrectState()
    {
        if (printerState == null)
        {
            try
            {
                printerState = Class.forName("me.aleksilassila.litematica.printer.printer.State")
                        .getField("CORRECT").get(null);
            }
            catch (ReflectiveOperationException exception)
            {
                throw new IllegalStateException("Unable to access printer state", exception);
            }
        }

        return printerState;
    }
}
