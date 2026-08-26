package net.isswr.blockfilter.mixin;

import fi.dy.masa.litematica.render.schematic.BufferAllocatorCache;
import fi.dy.masa.litematica.render.schematic.ChunkCacheSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderDataSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
import net.isswr.blockfilter.BlockFilterManager;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(ChunkRendererSchematicVbo.class)
public abstract class ChunkRendererSchematicVboMixin
{
    @Shadow(remap = false) protected ChunkCacheSchematic schematicWorldView;

    @Inject(method = "renderBlocksAndOverlay", at = @At("HEAD"), cancellable = true, remap = false)
    private void blockfilter$hideFilteredBlocks(BlockPos pos, ChunkRenderDataSchematic data,
                                                BufferAllocatorCache allocators,
                                                Set<BlockEntity> tileEntities,
                                                Set<RenderLayer> usedLayers,
                                                MatrixStack matrixStack, CallbackInfo ci)
    {
        if (this.schematicWorldView != null &&
            BlockFilterManager.shouldHide(this.schematicWorldView.getBlockState(pos)))
        {
            ci.cancel();
        }
    }
}
