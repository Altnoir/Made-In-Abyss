package com.altnoir.mia.worldgen.tree;

import com.altnoir.mia.init.MiaBlocks;
import com.altnoir.mia.worldgen.MiaFeatureUtils;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.util.valueproviders.WeightedListInt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.featuresize.TwoLayersFeatureSize;
import net.minecraft.world.level.levelgen.feature.foliageplacers.CherryFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.stateproviders.WeightedStateProvider;
import net.minecraft.world.level.levelgen.feature.treedecorators.BeehiveDecorator;
import net.minecraft.world.level.levelgen.feature.trunkplacers.CherryTrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.ForkingTrunkPlacer;

import java.util.List;

public class MiaTreeFeatures {
    public static final ResourceKey<ConfiguredFeature<?, ?>> SKYFOG_TREE = MiaFeatureUtils.resourceKey("skyfog_tree");
    public static final ResourceKey<ConfiguredFeature<?, ?>> FANCY_SKYFOG_TREE = MiaFeatureUtils.resourceKey("fancy_skyfog_tree");
    public static final ResourceKey<ConfiguredFeature<?, ?>> SKYFOG_TREE_BEES = MiaFeatureUtils.resourceKey("skyfog_tree_bees");
    public static final ResourceKey<ConfiguredFeature<?, ?>> FANCY_SKYFOG_TREE_BEES = MiaFeatureUtils.resourceKey("fancy_skyfog_tree_bees");

    public static void bootstrap(BootstrapContext<ConfiguredFeature<?, ?>> context) {

        var bee0002 = new BeehiveDecorator(0.002F);
        var bee001 = new BeehiveDecorator(0.01F);
        var bee002 = new BeehiveDecorator(0.02F);
        var bee005 = new BeehiveDecorator(0.05F);
        var bee1 = new BeehiveDecorator(1.0F);

        MiaFeatureUtils.register(context, SKYFOG_TREE, Feature.TREE, skyfog().build());
        MiaFeatureUtils.register(context, FANCY_SKYFOG_TREE, Feature.TREE, fancySkyfog().build());
        MiaFeatureUtils.register(context, SKYFOG_TREE_BEES, Feature.TREE, skyfog().decorators(List.of(bee002)).build());
        MiaFeatureUtils.register(context, FANCY_SKYFOG_TREE_BEES, Feature.TREE, fancySkyfog().decorators(List.of(bee005)).build());

    }

    private static TreeConfiguration.TreeConfigurationBuilder skyfog() {
        return new TreeConfiguration.TreeConfigurationBuilder(
                BlockStateProvider.simple(MiaBlocks.SKYFOG_LOG.get()),
                new ForkingTrunkPlacer(6, 2, 2),

                new WeightedStateProvider(
                        SimpleWeightedRandomList.<BlockState>builder()
                                .add(MiaBlocks.SKYFOG_LEAVES.get().defaultBlockState(), 5)
                                .add(MiaBlocks.SKYFOG_LEAVES_WITH_FRUITS.get().defaultBlockState(), 1)
                ),
                new CherryFoliagePlacer(ConstantInt.of(4), ConstantInt.of(0), ConstantInt.of(4), 0.25F, 0.5F, 0.16666667F, 0.33333334F),

                new TwoLayersFeatureSize(1, 0, 1)
        ).ignoreVines();
    }

    private static TreeConfiguration.TreeConfigurationBuilder fancySkyfog() {
        return new TreeConfiguration.TreeConfigurationBuilder(
                BlockStateProvider.simple(MiaBlocks.SKYFOG_LOG.get()),
                new CherryTrunkPlacer(7, 1, 0,
                        new WeightedListInt(
                                SimpleWeightedRandomList.<IntProvider>builder().add(ConstantInt.of(1), 1).add(ConstantInt.of(2), 1).add(ConstantInt.of(3), 1).build()
                        ),
                        UniformInt.of(2, 4), UniformInt.of(-4, -3), UniformInt.of(-1, 0)
                ),

                new WeightedStateProvider(
                        SimpleWeightedRandomList.<BlockState>builder()
                                .add(MiaBlocks.SKYFOG_LEAVES.get().defaultBlockState(), 4)
                                .add(MiaBlocks.SKYFOG_LEAVES_WITH_FRUITS.get().defaultBlockState(), 1)
                ),
                new CherryFoliagePlacer(ConstantInt.of(4), ConstantInt.of(0), ConstantInt.of(5), 0.25F, 0.5F, 0.16666667F, 0.33333334F),
                new TwoLayersFeatureSize(1, 0, 2)
        ).ignoreVines();
    }
}
