package io.github.vampirestudios.raa.registries;

import io.github.vampirestudios.raa.RandomlyAddingAnything;
import io.github.vampirestudios.raa.api.dimension.DimensionChunkGenerators;
import io.github.vampirestudios.raa.api.dimension.PlayerPlacementHandlers;
import io.github.vampirestudios.raa.api.enums.TextureTypes;
import io.github.vampirestudios.raa.api.namegeneration.NameGenerator;
import io.github.vampirestudios.raa.blocks.DimensionalBlock;
import io.github.vampirestudios.raa.blocks.DimensionalStone;
import io.github.vampirestudios.raa.blocks.PortalBlock;
import io.github.vampirestudios.raa.generation.dimensions.CustomDimension;
import io.github.vampirestudios.raa.generation.dimensions.CustomDimensionalBiome;
import io.github.vampirestudios.raa.generation.dimensions.data.*;
import io.github.vampirestudios.raa.generation.feature.tree.TreeType;
import io.github.vampirestudios.raa.history.Civilization;
import io.github.vampirestudios.raa.history.ProtoDimension;
import io.github.vampirestudios.raa.items.RAABlockItemAlt;
import io.github.vampirestudios.raa.items.dimension.*;
import io.github.vampirestudios.raa.utils.Rands;
import io.github.vampirestudios.raa.utils.RegistryUtils;
import io.github.vampirestudios.raa.utils.Utils;
import io.github.vampirestudios.raa.utils.debug.ConsolePrinting;
import io.github.vampirestudios.vampirelib.blocks.SlabBaseBlock;
import io.github.vampirestudios.vampirelib.blocks.StairsBaseBlock;
import io.github.vampirestudios.vampirelib.blocks.WallBaseBlock;
import io.github.vampirestudios.vampirelib.utils.Color;
import net.fabricmc.fabric.api.dimension.v1.FabricDimensionType;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.IceBlock;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.ToolMaterials;
import net.minecraft.recipe.Ingredient;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.registry.DefaultedRegistry;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.HorizontalVoronoiBiomeAccessType;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.surfacebuilder.SurfaceBuilder;
import net.minecraft.world.gen.surfacebuilder.TernarySurfaceConfig;

import java.util.*;

import static io.github.vampirestudios.raa.RandomlyAddingAnything.MOD_ID;
import static io.github.vampirestudios.raa.api.dimension.DimensionChunkGenerators.*;

public class Dimensions {
    public static final Set<Identifier> DIMENSION_NAMES = new HashSet<>();
    public static final Registry<DimensionData> DIMENSIONS = new DefaultedRegistry<>("raa:dimensions");

    public static void generate() {
        //pre generation of dimensions: basic data, flags, and name
        //This is only the data needed for civilization simulation
        ArrayList<ProtoDimension> protoDimensions = new ArrayList<>();
        for (int a = 0; a < RandomlyAddingAnything.CONFIG.dimensionGenAmount; a++) {
            float temperature = Rands.randFloat(2.0F);
            int flags = generateDimensionFlags();

            NameGenerator nameGenerator = RandomlyAddingAnything.CONFIG.namingLanguage.getDimensionNameGenerator();
            Pair<String, Identifier> name = nameGenerator.generateUnique(DIMENSION_NAMES, MOD_ID);
            DIMENSION_NAMES.add(name.getRight());

            protoDimensions.add(new ProtoDimension(name, flags, temperature, Rands.randFloat(2F)));
        }

        for (ProtoDimension dimension : protoDimensions) {
            dimension.setXandY(Rands.randFloatRange(0, 1), Rands.randFloatRange(0, 1));
        }

        //perform the civilization handling

        //generate the civilizations
        ArrayList<Civilization> civs = new ArrayList<>();
        Set<Identifier> civNames = new HashSet<>();
        Set<ProtoDimension> usedDimensions = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            NameGenerator nameGenerator = RandomlyAddingAnything.CONFIG.namingLanguage.getDimensionNameGenerator();
            Pair<String, Identifier> name = nameGenerator.generateUnique(civNames, MOD_ID);
            civNames.add(name.getRight());
            ProtoDimension generatedDimension = Rands.list(protoDimensions);
            if (usedDimensions.contains(generatedDimension)) continue;
            else usedDimensions.add(generatedDimension);
            civs.add(new Civilization(name.getLeft(), generatedDimension));
        }

        //tick the civs and get their influence
        civs.forEach(Civilization::simulate);

        for (Civilization civ : civs) {

            //tech level 0 civs get no influence
            if (civ.getTechLevel() == 0) continue;

            for (ProtoDimension dimension : protoDimensions) {
                if (dimension != civ.getHomeDimension()) {
                    //get distance to set the influence radius
                    double d = Utils.dist(dimension.getX(), dimension.getY(), civ.getHomeDimension().getX(), civ.getHomeDimension().getY());
                    if (d <= civ.getInfluenceRadius()) { //if the dimension is within the influence radius, it has an influence
                        double percent = (civ.getInfluenceRadius() - d) / civ.getInfluenceRadius();
                        dimension.addInfluence(civ.getName(), percent);

                        //modify the dimension based on the civ tech level and influence
                        if (percent > 0.40) {
                            if (civ.getTechLevel() >= 2) if (Rands.chance(5)) dimension.setAbandoned();
                        }
                        if (percent > 0.60) {
                            if (civ.getTechLevel() >= 2) if (Rands.chance(4)) dimension.setAbandoned();
                            if (civ.getTechLevel() >= 3) if (Rands.chance(5)) dimension.setDead();
                        }
                        if (percent > 0.80) {
                            if (civ.getTechLevel() >= 2) if (Rands.chance(3)) dimension.setAbandoned();
                            if (civ.getTechLevel() >= 3) if (Rands.chance(4)) dimension.setDead();
                        }
                        if (percent > 0.70) {
                            if (civ.getTechLevel() >= 3) dimension.setCivilized();
                        }
                    }
                } else {
                    //a civ's home dimension has 100% influence by that civ
                    dimension.addInfluence(civ.getName(), 1.0);
                }

                //Ensure that both dead and lush flags don't coexist
                if (Utils.checkBitFlag(dimension.getFlags(), Utils.DEAD) && Utils.checkBitFlag(dimension.getFlags(), Utils.LUSH))
                    dimension.removeLush();
            }
        }

        //post generation of dimensions: do everything to actually register the dimension
        for (ProtoDimension dimension : protoDimensions) {
            int difficulty = 0;
            int flags = dimension.getFlags();
            Pair<String, Identifier> name = dimension.getName();
            float hue = Rands.randFloatRange(0, 1.0F);
            float foliageColor = hue + Rands.randFloatRange(-0.15F, 0.15F);
            float stoneColor = hue + Rands.randFloatRange(-0.45F, 0.45F);
            float fogHue = hue + 0.3333f;
            float skyHue = fogHue + 0.3333f;

            float saturation = Rands.randFloatRange(0.5F, 1.0F);
            float stoneSaturation = Rands.randFloatRange(0.2F, 0.6F);
            if (Utils.checkBitFlag(flags, Utils.DEAD)) {
                saturation = Rands.randFloatRange(0.0F, 0.2F);
                stoneSaturation = saturation;
                difficulty += 2;
                if (Utils.checkBitFlag(flags, Utils.CIVILIZED)) difficulty++;
            }
            if (Utils.checkBitFlag(flags, Utils.LUSH)) saturation = Rands.randFloatRange(0.7F, 1.0F);
            if (Utils.checkBitFlag(flags, Utils.CORRUPTED)) difficulty += 2;
            if (Utils.checkBitFlag(flags, Utils.MOLTEN)) difficulty += 2;
            if (Utils.checkBitFlag(flags, Utils.DRY)) difficulty += 2;
            if (Utils.checkBitFlag(flags, Utils.TECTONIC)) difficulty++;
            float value = Rands.randFloatRange(0.5F, 1.0F);
            Color GRASS_COLOR = new Color(Color.HSBtoRGB(hue, saturation, value));
            Color FOLIAGE_COLOR = new Color(Color.HSBtoRGB(foliageColor, saturation, value));
            Color FOG_COLOR = new Color(Color.HSBtoRGB(fogHue, saturation, value));
            Color SKY_COLOR = new Color(Color.HSBtoRGB(skyHue, saturation, value));
            Color WATER_COLOR = new Color(Color.HSBtoRGB(skyHue, saturation + Rands.randFloatRange(0.3F, 1.0F), value + Rands.randFloatRange(0.3F, 1.0F)));
            Color STONE_COLOR = new Color(Color.HSBtoRGB(stoneColor, stoneSaturation, value));
            Color MOON_COLOR = new Color(Color.HSBtoRGB(hue, saturation, value));
            Color SUN_COLOR = new Color(Color.HSBtoRGB(hue, saturation, value));

            Color BLOOD_MOON = new Color(Color.HSBtoRGB(0F, 100F, 71F));
            Color BLUE_MOON = new Color(Color.HSBtoRGB(196F, 69F, 65F));
            Color VENUS = new Color(Color.HSBtoRGB(345F, 2.7F, 58.04F));

            DimensionChunkGenerators gen = Utils.randomCG(Rands.randIntRange(0, 100));
            if (gen == DimensionChunkGenerators.FLOATING) difficulty++;
            if (gen == CAVES) difficulty += 2;
            float scale = dimension.getScale();
            if (scale > 0.8) difficulty++;
            if (scale > 1.6) difficulty++;
            Pair<Integer, HashMap<String, int[]>> difficultyAndMobs = generateDimensionMobs(flags, difficulty);

            float gravity = 1F;
            if (Rands.chance(6)) {
                gravity = Rands.randFloatRange(0.75F, 1.25F);
            } else if(Rands.chance(12)) {
                gravity = Rands.randFloatRange(0.25F, 1.75F);
            }

            if (Rands.chance(10)) {
                MOON_COLOR = BLOOD_MOON;
            } else if (Rands.chance(60)) {
                MOON_COLOR = BLUE_MOON;
            } else if (Rands.chance(100)) {
                MOON_COLOR = VENUS;
            }

            DimensionData.Builder builder = DimensionData.Builder.create(name.getRight(), name.getLeft())
                    .canSleep(Rands.chance(4))
                    .waterVaporize(Rands.chance(100))
                    .shouldRenderFog(Rands.chance(40))
                    .chunkGenerator(gen)
                    .flags(flags)
                    .difficulty(difficultyAndMobs.getLeft())
                    .mobs(difficultyAndMobs.getRight())
                    .civilizationInfluences(dimension.getCivilizationInfluences())
                    .cloudHeight(Rands.randFloatRange(80F, 256F))
                    .stoneHardness(Rands.randFloatRange(0.2f, 5f), Rands.randFloatRange(3, 18))
                    .stoneJumpHeight(Rands.randFloatRange(1.0F, 10.0F))
                    .gravity(gravity);

            DimensionTextureData texturesInformation = DimensionTextureData.Builder.create()
                    .stoneTexture(Rands.list(TextureTypes.STONE_TEXTURES))
                    .stoneBricksTexture(Rands.list(TextureTypes.STONE_BRICKS_TEXTURES))
                    .mossyStoneBricksTexture(Rands.list(TextureTypes.MOSSY_STONE_BRICKS_TEXTURES))
                    .crackedStoneBricksTexture(Rands.list(TextureTypes.CRACKED_STONE_BRICKS_TEXTURES))
                    .cobblestoneTexture(Rands.list(TextureTypes.COBBLESTONE_TEXTURES))
                    .mossyCobblestoneTexture(Rands.list(TextureTypes.MOSSY_COBBLESTONE_TEXTURES))
                    .chiseledTexture(Rands.list(TextureTypes.CHISELED_STONE_TEXTURES))
                    .mossyChiseledTexture(Rands.list(TextureTypes.MOSSY_CHISELED_STONE_TEXTURES))
                    .crackedChiseledTexture(Rands.list(TextureTypes.CRACKED_CHISELED_STONE_TEXTURES))
                    .polishedTexture(Rands.list(TextureTypes.POLISHED_STONE_TEXTURES))
                    .iceTexture(Rands.list(TextureTypes.ICE_TEXTURES))
                    .build();
            builder.texturesInformation(texturesInformation);

            //TODO: make proper number generation

            for (int i = 0; i < Rands.randIntRange(1, 20); i++) {
                float grassColor = hue + Rands.randFloatRange(-0.25f, 0.25f);
                List<DimensionTreeData> treeDataList = new ArrayList<>();

                TreeType treeType = TreeType.MULTIPLE_TREE_FOREST;
                if (Rands.chance(7)) treeType = TreeType.PLAINS_TREES;
                else if (Rands.chance(3)) treeType = TreeType.SINGLE_TREE_FOREST;
                if (Utils.checkBitFlag(flags, Utils.LUSH)) treeType = TreeType.MULTIPLE_TREE_FOREST;

                if (treeType != TreeType.PLAINS_TREES) {
                    int treeAmount = Rands.randIntRange(1, 3);
                    if (Utils.checkBitFlag(flags, Utils.LUSH)) treeAmount = 11;

                    if (treeType == TreeType.SINGLE_TREE_FOREST) {
                        treeAmount = 1;
                    }

                    for (int j = 0; j < treeAmount; j++) {
                        DimensionTreeData treeData = DimensionTreeData.Builder.create()
                                .plainsTrees(Rands.chance(10))
                                .woodType(Rands.list(Arrays.asList(DimensionWoodType.values())))
                                .foliagePlacerType(Rands.list(Arrays.asList(DimensionFoliagePlacers.values())))
                                .treeType(Rands.list(Arrays.asList(DimensionTreeTypes.values())))
                                .baseHeight(Rands.randIntRange(2, 24))
                                .maxWaterDepth(Rands.randIntRange(0, 8))
                                .foliageHeight(Rands.randIntRange(1, 2))
                                .foliageRange(Rands.randIntRange(1, 3))
                                .chance(Rands.randFloatRange(0.05f, 3.5f))
                                .hasCocoaBeans(Rands.chance(3))
                                .hasLeafVines(Rands.chance(3))
                                .hasPodzolUnderneath(Rands.chance(3))
                                .hasTrunkVines(Rands.chance(3))
                                .build();
                        treeDataList.add(treeData);
                    }
                }

                SurfaceBuilder<?> surfaceBuilder = Utils.newRandomSurfaceBuilder();
                TernarySurfaceConfig surfaceConfig = Utils.randomSurfaceBuilderConfig();

                DimensionBiomeData biomeData = DimensionBiomeData.Builder.create(Utils.addSuffixToPath(name.getRight(), "_biome" + "_" + i), name.getLeft())
                        .depth(Rands.randFloatRange(-1F, 3F))
                        .scale(Math.max(scale + Rands.randFloatRange(-0.75f, 0.75f), 0)) //ensure the scale is never below 0
                        .temperature(dimension.getTemperature() + Rands.randFloatRange(-0.5f, 0.5f))
                        .downfall(Rands.randFloat(1F))
                        .waterColor(WATER_COLOR.getColor())
                        .grassColor(new Color(Color.HSBtoRGB(grassColor, saturation, value)).getColor())
                        .foliageColor(new Color(Color.HSBtoRGB(grassColor + Rands.randFloatRange(-0.1f, 0.1f), saturation, value)).getColor())
                        .treeType(treeType)
                        .treeData(treeDataList)
                        .largeSkeletonTreeChance(Rands.randFloatRange(0, 0.5F))
                        .spawnsCratersInNonCorrupted(Rands.chance(4))
                        //TODO: make these based on civ tech level
                        .campfireChance(Rands.randFloatRange(0.003F, 0.005F))
                        .outpostChance(Rands.randFloatRange(0.001F, 0.003F))
                        .towerChance(Rands.randFloatRange(0.001F, 0.0015F))
                        .hasMushrooms(Rands.chance(6))
                        .hasMossyRocks(Rands.chance(8))
                        .nonCorruptedCratersChance(Rands.randFloatRange(0, 0.05F))
                        .corruptedCratersChance(Rands.randFloatRange(0, 0.05F))
                        .surfaceBuilder(Registry.SURFACE_BUILDER.getId(surfaceBuilder))
                        .surfaceConfig(Utils.fromConfigToIdentifier(surfaceConfig))
                        .build();
                builder.biome(biomeData);
            }

            DimensionColorPalette colorPalette = DimensionColorPalette.Builder.create()
                    .skyColor(SKY_COLOR.getColor())
                    .grassColor(GRASS_COLOR.getColor())
                    .fogColor(FOG_COLOR.getColor())
                    .foliageColor(FOLIAGE_COLOR.getColor())
                    .stoneColor(STONE_COLOR.getColor()).build();
            builder.colorPalette(colorPalette);

            DimensionCustomSkyInformation customSkyInformation = DimensionCustomSkyInformation.Builder.create()
                    .hasSkyLight(Rands.chance(1))
                    .hasSky(!Rands.chance(2))
                    .customSun(Rands.chance(2))
                    .sunSize(Rands.randFloatRange(30F, 120F))
                    .sunTint(SUN_COLOR.getColor())
                    .customMoon(Rands.chance(2))
                    .moonSize(Rands.randFloatRange(20F, 80F))
                    .moonTint(MOON_COLOR.getColor()).build();
            builder.customSkyInformation(customSkyInformation);

            DimensionData dimensionData = builder.build();

            Registry.register(DIMENSIONS, dimensionData.getId(), dimensionData);

            // Debug Only
            if (RandomlyAddingAnything.CONFIG.debug) {
                ConsolePrinting.dimensionDebug(dimensionData);
            }
        }
    }

    public static void createDimensions() {
        DIMENSIONS.forEach(dimensionData -> {
            Identifier identifier = new Identifier(MOD_ID, dimensionData.getName().toLowerCase());

            Block stoneBlock = RegistryUtils.register(new DimensionalStone(dimensionData), Utils.addSuffixToPath(identifier, "_stone"),
                    RandomlyAddingAnything.RAA_DIMENSION_BLOCKS, dimensionData.getName(), "stone");
            RegistryUtils.register(new StairsBaseBlock(stoneBlock.getDefaultState()), Utils.addSuffixToPath(identifier, "_stone_stairs"),
                    RandomlyAddingAnything.RAA_DIMENSION_BLOCKS, dimensionData.getName(), "stoneStairs");
            RegistryUtils.register(new SlabBaseBlock(Block.Settings.copy(Blocks.STONE_SLAB)), Utils.addSuffixToPath(identifier, "_stone_slab"),
                    RandomlyAddingAnything.RAA_DIMENSION_BLOCKS, dimensionData.getName(), "stoneSlab");
            RegistryUtils.register(new WallBaseBlock(Block.Settings.copy(Blocks.COBBLESTONE_WALL)), Utils.addSuffixToPath(identifier, "_stone_wall"),
                    RandomlyAddingAnything.RAA_DIMENSION_BLOCKS, dimensionData.getName(), "stoneWall");

            Set<Biome> biomes = new LinkedHashSet<>();
            for (int i = 0; i < dimensionData.getBiomeData().size(); i++) {
                CustomDimensionalBiome biome = new CustomDimensionalBiome(dimensionData, dimensionData.getBiomeData().get(i));
                biomes.add(RegistryUtils.registerBiome(dimensionData.getBiomeData().get(i).getId(), biome));
            }

            FabricDimensionType.Builder builder = FabricDimensionType.builder()
                    .biomeAccessStrategy(HorizontalVoronoiBiomeAccessType.INSTANCE)
                    .skyLight(dimensionData.getCustomSkyInformation().hasSkyLight())
                    .factory((world, dimensionType) -> new CustomDimension(world, dimensionType, dimensionData, biomes, Rands.chance(50) ? Blocks.STONE : stoneBlock));

            if (dimensionData.getDimensionChunkGenerator() == CAVES || dimensionData.getDimensionChunkGenerator() == FLAT_CAVES || dimensionData.getDimensionChunkGenerator() == HIGH_CAVES) {
                builder.defaultPlacer(PlayerPlacementHandlers.CAVE_WORLD.getEntityPlacer());
            } else if (dimensionData.getDimensionChunkGenerator() == FLOATING || dimensionData.getDimensionChunkGenerator() == LAYERED_FLOATING || dimensionData.getDimensionChunkGenerator() == PRE_CLASSIC_FLOATING) {
                builder.defaultPlacer(PlayerPlacementHandlers.FLOATING_WORLD.getEntityPlacer());
            } else {
                builder.defaultPlacer(PlayerPlacementHandlers.SURFACE_WORLD.getEntityPlacer());
            }

            DimensionType type = builder.buildAndRegister(dimensionData.getId());
            DimensionType dimensionType;
            if (Registry.DIMENSION_TYPE.get(dimensionData.getId()) == null) {
                dimensionType = Registry.register(Registry.DIMENSION_TYPE, dimensionData.getId(), type);
            }
            else {
                dimensionType = Registry.DIMENSION_TYPE.get(dimensionData.getId());
            }

//            RegistryUtils.registerBlockWithoutItem(new CustomPortalBlock(dimensionData, dimensionType), Utils.addSuffixToPath(dimensionData.getId(), "_custom_portal"));

            //TODO: custom tool durabilities
            ToolMaterial toolMaterial = new ToolMaterial() {
                @Override
                public int getDurability() {
                    return (int) (ToolMaterials.STONE.getDurability() * dimensionData.getStoneHardness() / 2);
                }

                @Override
                public float getMiningSpeedMultiplier() {
                    return ToolMaterials.STONE.getMiningSpeedMultiplier() * dimensionData.getStoneHardness() / 2;
                }

                @Override
                public float getAttackDamage() {
                    return ToolMaterials.STONE.getAttackDamage() * dimensionData.getStoneHardness() / 4;
                }

                @Override
                public int getMiningLevel() {
                    return ToolMaterials.STONE.getMiningLevel();
                }

                @Override
                public int getEnchantability() {
                    return (int) (ToolMaterials.STONE.getEnchantability() * dimensionData.getStoneHardness() / 4);
                }

                @Override
                public Ingredient getRepairIngredient() {
                    return Ingredient.ofItems(Registry.ITEM.get(Utils.addSuffixToPath(identifier, "_cobblestone")));
                }
            };

            RegistryUtils.registerItem(
                    new DimensionalPickaxeItem(
                            dimensionData,
                            toolMaterial,
                            1,
                            -2.8F,
                            new Item.Settings().group(RandomlyAddingAnything.RAA_TOOLS).recipeRemainder(Registry.ITEM.get(Utils.addSuffixToPath(identifier, "_cobblestone")))
                    ),
                    Utils.addSuffixToPath(identifier, "_pickaxe")
            );
            RegistryUtils.registerItem(
                    new DimensionalAxeItem(
                            dimensionData,
                            toolMaterial,
                            7.0F,
                            -3.2F,
                            new Item.Settings().group(RandomlyAddingAnything.RAA_TOOLS).recipeRemainder(Registry.ITEM.get(Utils.addSuffixToPath(identifier, "_cobblestone")))
                    ),
                    Utils.addSuffixToPath(identifier, "_axe")
            );
            RegistryUtils.registerItem(
                    new DimensionalShovelItem(
                            dimensionData,
                            toolMaterial,
                            1.5F,
                            -3.0F,
                            new Item.Settings().group(RandomlyAddingAnything.RAA_TOOLS).recipeRemainder(Registry.ITEM.get(Utils.addSuffixToPath(identifier, "_cobblestone")))
                    ),
                    Utils.addSuffixToPath(identifier, "_shovel")
            );
            RegistryUtils.registerItem(
                    new DimensionalHoeItem(
                            dimensionData,
                            toolMaterial,
                            1.5F,
                            -2.0F,
                            new Item.Settings().group(RandomlyAddingAnything.RAA_TOOLS).recipeRemainder(Registry.ITEM.get(Utils.addSuffixToPath(identifier, "_cobblestone")))
                    ),
                    Utils.addSuffixToPath(identifier, "_hoe")
            );
            RegistryUtils.registerItem(
                    new DimensionalSwordItem(
                            toolMaterial,
                            dimensionData,
                            new Item.Settings().group(RandomlyAddingAnything.RAA_WEAPONS).recipeRemainder(Registry.ITEM.get(Utils.addSuffixToPath(identifier, "_cobblestone")))
                    ),
                    Utils.addSuffixToPath(identifier, "_sword")
            );
//            RegistryUtils.registerItem(new DimensionalPortalKeyItem(dimensionData), Utils.addSuffixToPath(identifier, "_portal_key"));

            Block stoneBrick = RegistryUtils.register(new DimensionalBlock(), new Identifier(RandomlyAddingAnything.MOD_ID,
                            dimensionData.getName().toLowerCase() + "_stone_bricks"),
                    RandomlyAddingAnything.RAA_DIMENSION_BLOCKS, dimensionData.getName(), "stoneBricks");
            RegistryUtils.register(new StairsBaseBlock(stoneBrick.getDefaultState()), Utils.addSuffixToPath(identifier, "_stone_brick_stairs"),
                    RandomlyAddingAnything.RAA_DIMENSION_BLOCKS, dimensionData.getName(), "stoneBrickStairs");
            RegistryUtils.register(new SlabBaseBlock(Block.Settings.copy(Blocks.STONE_SLAB)), Utils.addSuffixToPath(identifier, "_stone_brick_slab"),
                    RandomlyAddingAnything.RAA_DIMENSION_BLOCKS, dimensionData.getName(), "stoneBrickSlab");
            RegistryUtils.register(new WallBaseBlock(Block.Settings.copy(Blocks.COBBLESTONE_WALL)), Utils.addSuffixToPath(identifier, "_stone_brick_wall"),
                    RandomlyAddingAnything.RAA_DIMENSION_BLOCKS, dimensionData.getName(), "stoneBrickWall");
            /*RegistryUtils.register(new DimensionalBlock(), new Identifier(RandomlyAddingAnything.MOD_ID,
                            "mossy_" + dimension.getName().toLowerCase() + "_stone_bricks"),
                    RandomlyAddingAnything.RAA_DIMENSION_BLOCKS, dimension.getName(), "mossyStoneBricks");
            RegistryUtils.register(new DimensionalBlock(), new Identifier(RandomlyAddingAnything.MOD_ID,
                            "cracked_" + dimension.getName().toLowerCase() + "_stone_bricks"),
                    RandomlyAddingAnything.RAA_DIMENSION_BLOCKS, dimension.getName(), "crackedStoneBricks");*/
            Block cobblestone = RegistryUtils.register(new DimensionalBlock(), new Identifier(RandomlyAddingAnything.MOD_ID,
                            dimensionData.getName().toLowerCase() + "_cobblestone"),
                    RandomlyAddingAnything.RAA_DIMENSION_BLOCKS, dimensionData.getName(), "cobblestone");
            RegistryUtils.register(new StairsBaseBlock(cobblestone.getDefaultState()), Utils.addSuffixToPath(identifier, "_cobblestone_stairs"),
                    RandomlyAddingAnything.RAA_DIMENSION_BLOCKS, dimensionData.getName(), "cobblestoneStairs");
            RegistryUtils.register(new SlabBaseBlock(Block.Settings.copy(Blocks.STONE_SLAB)), Utils.addSuffixToPath(identifier, "_cobblestone_slab"),
                    RandomlyAddingAnything.RAA_DIMENSION_BLOCKS, dimensionData.getName(), "cobblestoneSlab");
            RegistryUtils.register(new WallBaseBlock(Block.Settings.copy(Blocks.COBBLESTONE_WALL)), Utils.addSuffixToPath(identifier, "_cobblestone_wall"),
                    RandomlyAddingAnything.RAA_DIMENSION_BLOCKS, dimensionData.getName(), "cobblestoneWall");
            /*RegistryUtils.register(new DimensionalBlock(), new Identifier(RandomlyAddingAnything.MOD_ID,
                            dimension.getName().toLowerCase() + "_mossy_cobblestone"),
                    RandomlyAddingAnything.RAA_DIMENSION_BLOCKS, dimension.getName(), "mossyCobblestone");*/
            RegistryUtils.register(new DimensionalBlock(), new Identifier(RandomlyAddingAnything.MOD_ID,
                            "chiseled_" + dimensionData.getName().toLowerCase() + "_stone_bricks"),
                    RandomlyAddingAnything.RAA_DIMENSION_BLOCKS, dimensionData.getName(), "chiseled_stone_bricks");
            /*RegistryUtils.register(new DimensionalBlock(), new Identifier(RandomlyAddingAnything.MOD_ID,
                            "cracked_chiseled_" + dimension.getName().toLowerCase()),
                    RandomlyAddingAnything.RAA_DIMENSION_BLOCKS, dimension.getName(), "crackedChiseled");
            RegistryUtils.register(new DimensionalBlock(), new Identifier(RandomlyAddingAnything.MOD_ID,
                            "mossy_chiseled_" + dimension.getName().toLowerCase()),
                    RandomlyAddingAnything.RAA_DIMENSION_BLOCKS, dimension.getName(), "mossyChiseled");*/
            Block polished = RegistryUtils.register(new DimensionalBlock(), new Identifier(RandomlyAddingAnything.MOD_ID,
                            "polished_" + dimensionData.getName().toLowerCase()),
                    RandomlyAddingAnything.RAA_DIMENSION_BLOCKS, dimensionData.getName(), "polished");
            RegistryUtils.register(new StairsBaseBlock(polished.getDefaultState()), Utils.addPrefixAndSuffixToPath(identifier, "polished_", "_stairs"),
                    RandomlyAddingAnything.RAA_DIMENSION_BLOCKS, dimensionData.getName(), "polishedStairs");
            RegistryUtils.register(new SlabBaseBlock(Block.Settings.copy(Blocks.STONE_SLAB)), Utils.addPrefixAndSuffixToPath(identifier, "polished_", "_slab"),
                    RandomlyAddingAnything.RAA_DIMENSION_BLOCKS, dimensionData.getName(), "polishedSlab");
            RegistryUtils.register(new WallBaseBlock(Block.Settings.copy(Blocks.COBBLESTONE_WALL)), Utils.addPrefixAndSuffixToPath(identifier, "polished_", "_wall"),
                    RandomlyAddingAnything.RAA_DIMENSION_BLOCKS, dimensionData.getName(), "polishedWall");

            RegistryUtils.register(new IceBlock(Block.Settings.copy(Blocks.ICE)), new Identifier(RandomlyAddingAnything.MOD_ID,
                            dimensionData.getName().toLowerCase() + "_ice"),
                    RandomlyAddingAnything.RAA_DIMENSION_BLOCKS, dimensionData.getName(), "ice");

            Block portalBlock = RegistryUtils.registerBlockWithoutItem(new PortalBlock(dimensionType, dimensionData),
                    new Identifier(RandomlyAddingAnything.MOD_ID, dimensionData.getName().toLowerCase() + "_portal"));
            RegistryUtils.registerItem(new RAABlockItemAlt(dimensionData.getName(), "portal", portalBlock, new Item.Settings().group(ItemGroup.TRANSPORTATION)),
                    new Identifier(RandomlyAddingAnything.MOD_ID, dimensionData.getName().toLowerCase() + "_portal"));
        });
    }

    public static Pair<Integer, HashMap<String, int[]>> generateDimensionMobs(int flags, int difficulty) {
        HashMap<String, int[]> list = new HashMap<>();
        if (Utils.checkBitFlag(flags, Utils.LUSH)) {
            String[] names = new String[]{"cow", "pig", "chicken", "horse", "donkey", "sheep", "llama"};
            for (String name : names) {
                int spawnSize = Rands.randIntRange(4, 16);
                list.put(name, new int[]{Rands.randIntRange(1, 300), spawnSize, spawnSize + Rands.randIntRange(2, 8)});
            }
        } else {
            if (!Utils.checkBitFlag(flags, Utils.DEAD)) {
                if (Rands.chance(2)) {
                    int spawnSize = Rands.randIntRange(2, 12);
                    list.put("cow", new int[]{Rands.randIntRange(1, 300), spawnSize, spawnSize + Rands.randIntRange(2, 4)});
                } else {
                    difficulty++;
                }
                if (Rands.chance(2)) {
                    int spawnSize = Rands.randIntRange(2, 12);
                    list.put("pig", new int[]{Rands.randIntRange(1, 300), spawnSize, spawnSize + Rands.randIntRange(2, 4)});
                } else {
                    difficulty++;
                }
                if (Rands.chance(2)) {
                    int spawnSize = Rands.randIntRange(2, 12);
                    list.put("chicken", new int[]{Rands.randIntRange(1, 300), spawnSize, spawnSize + Rands.randIntRange(2, 4)});
                } else {
                    difficulty++;
                }
                if (Rands.chance(2)) {
                    int spawnSize = Rands.randIntRange(2, 8);
                    list.put("horse", new int[]{Rands.randIntRange(1, 300), spawnSize, spawnSize + Rands.randIntRange(2, 4)});
                }
                if (Rands.chance(2)) {
                    int spawnSize = Rands.randIntRange(2, 8);
                    list.put("donkey", new int[]{Rands.randIntRange(1, 300), spawnSize, spawnSize + Rands.randIntRange(2, 4)});
                }
                if (Rands.chance(2)) {
                    int spawnSize = Rands.randIntRange(2, 12);
                    list.put("sheep", new int[]{Rands.randIntRange(1, 300), spawnSize, spawnSize + Rands.randIntRange(2, 4)});
                } else {
                    difficulty++;
                }
                if (Rands.chance(2)) {
                    int spawnSize = Rands.randIntRange(2, 8);
                    list.put("llama", new int[]{Rands.randIntRange(1, 300), spawnSize, spawnSize + Rands.randIntRange(2, 4)});
                }
            } else {
                difficulty += 4;
            }
        }
        if (Rands.chance(2)) {
            int spawnSize = Rands.randIntRange(2, 12);
            list.put("bat", new int[]{Rands.randIntRange(1, 300), spawnSize, spawnSize + Rands.randIntRange(2, 4)});
        }
        if (Rands.chance(2)) {
            int spawnSize = Rands.randIntRange(2, 8);
            list.put("spider", new int[]{Rands.randIntRange(1, 300), spawnSize, spawnSize + Rands.randIntRange(2, 4)});
        } else {
            difficulty--;
        }
        if (Rands.chance(2)) {
            int spawnSize = Rands.randIntRange(2, 12);
            list.put("zombie", new int[]{Rands.randIntRange(1, 300), spawnSize, spawnSize + Rands.randIntRange(2, 4)});
        } else {
            difficulty--;
        }
        if (Rands.chance(2)) {
            int spawnSize = Rands.randIntRange(2, 4);
            list.put("zombie_villager", new int[]{Rands.randIntRange(1, 300), spawnSize, spawnSize + 1});
        } else {
            --difficulty;
        }
        if (Rands.chance(2)) {
            int spawnSize = Rands.randIntRange(2, 12);
            list.put("skeleton", new int[]{Rands.randIntRange(1, 300), spawnSize, spawnSize + Rands.randIntRange(2, 4)});
        } else {
            difficulty--;
        }
        if (Rands.chance(2)) {
            int spawnSize = Rands.randIntRange(2, 8);
            list.put("creeper", new int[]{Rands.randIntRange(1, 300), spawnSize, spawnSize + Rands.randIntRange(2, 4)});
        } else {
            difficulty -= 2;
        }
        if (Rands.chance(2)) {
            int spawnSize = Rands.randIntRange(2, 4);
            list.put("slime", new int[]{Rands.randIntRange(1, 300), spawnSize, spawnSize + Rands.randIntRange(2, 4)});
        } else {
            difficulty--;
        }
        if (Rands.chance(2)) {
            int spawnSize = Rands.randIntRange(2, 4);
            list.put("enderman", new int[]{Rands.randIntRange(1, 300), spawnSize, spawnSize});
        } else {
            difficulty -= 2;
        }
        if (Rands.chance(2)) {
            int spawnSize = Rands.randIntRange(2, 3);
            list.put("witch", new int[]{Rands.randIntRange(1, 300), spawnSize, spawnSize});
        } else {
            difficulty -= 2;
        }

        if (Rands.chance(10)) {
            int spawnSize = Rands.randIntRange(2, 3);
            list.put("blaze", new int[]{Rands.randIntRange(1, 300), spawnSize, spawnSize});
        } else {
            difficulty -= 2;
        }
        if (Rands.chance(10)) {
            int spawnSize = Rands.randIntRange(2, 3);
            list.put("piglin", new int[]{Rands.randIntRange(1, 300), spawnSize, spawnSize});
        } else {
            difficulty -= 2;
        }
        if (Rands.chance(10)) {
            int spawnSize = Rands.randIntRange(2, 3);
            list.put("zombified_piglin", new int[]{Rands.randIntRange(1, 300), spawnSize, spawnSize});
        } else {
            difficulty -= 2;
        }
        if (Rands.chance(10)) {
            int spawnSize = Rands.randIntRange(2, 3);
            list.put("ghast", new int[]{Rands.randIntRange(1, 300), spawnSize, spawnSize});
        } else {
            difficulty -= 2;
        }
        return new Pair<>(difficulty, list);
    }

    //generate the flags for a dimension, ensures that dimensions can't have conflicting flags
    public static int generateDimensionFlags() {
        int flags = 0;

        //post apocalyptic dimensions are a rare combination of the worst flags
        if (Rands.chance(35)) {
            flags = Utils.POST_APOCALYPTIC;
            return flags;
        }

        //any dimension can be a lucid dimension
        //TODO: fix skybox
//        if (Rands.chance(65)) {
//            flags |= Utils.LUCID;
//        }

        //any dimension can be a tectonic dimension
        if (Rands.chance(10)) {
            flags |= Utils.TECTONIC;
        }

        //corrupted dimensions
        if (Rands.chance(20)) {
            flags |= Utils.CORRUPTED;
            if (Rands.chance(8)) {
                flags |= Utils.DEAD;
            }
            if (Rands.chance(3)) {
                flags |= Utils.MOLTEN;
            }
            if (Rands.chance(4)) {
                flags |= Utils.DRY;
            }
        } else {
            //dead dimensions
            if (Rands.chance(18)) {
                flags |= Utils.DEAD;
                if (Rands.chance(6)) {
                    flags |= Utils.MOLTEN;
                }
                if (Rands.chance(5)) {
                    flags |= Utils.DRY;
                }
            } else { // lush dimensions
                if (Rands.chance(4)) {
                    flags |= Utils.LUSH;
                }

                if (Rands.chance(10)) {
                    flags |= Utils.FROZEN;
                }
            }
        }

        return flags;
    }
}