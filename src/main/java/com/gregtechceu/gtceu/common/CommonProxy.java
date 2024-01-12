package com.gregtechceu.gtceu.common;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.addon.AddonFinder;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.capability.forge.GTCapability;
import com.gregtechceu.gtceu.api.data.chemical.material.event.MaterialEvent;
import com.gregtechceu.gtceu.api.data.chemical.material.event.MaterialRegistryEvent;
import com.gregtechceu.gtceu.api.data.chemical.material.event.PostMaterialEvent;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialIconSet;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialIconType;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.data.worldgen.WorldGenLayers;
import com.gregtechceu.gtceu.api.data.worldgen.strata.StrataGenerationType;
import com.gregtechceu.gtceu.api.gui.factory.CoverUIFactory;
import com.gregtechceu.gtceu.api.gui.factory.GTUIEditorFactory;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIFactory;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredient;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.client.screen.CreateStrataWorldScreen;
import com.gregtechceu.gtceu.common.block.StoneTypes;
import com.gregtechceu.gtceu.common.data.*;
import com.gregtechceu.gtceu.common.data.materials.GTFoods;
import com.gregtechceu.gtceu.common.item.tool.forge.ToolLootModifier;
import com.gregtechceu.gtceu.common.registry.GTRegistration;
import com.gregtechceu.gtceu.common.unification.material.MaterialRegistryManager;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.data.GregTechDatagen;
import com.gregtechceu.gtceu.data.lang.MaterialLangGenerator;
import com.gregtechceu.gtceu.forge.AlloyBlastPropertyAddition;
import com.gregtechceu.gtceu.integration.kjs.GTCEuServerEvents;
import com.gregtechceu.gtceu.integration.kjs.GTCEuStartupEvents;
import com.gregtechceu.gtceu.integration.kjs.GTRegistryInfo;
import com.gregtechceu.gtceu.integration.kjs.events.GTOreVeinEventJS;
import com.gregtechceu.gtceu.integration.kjs.events.MaterialModificationEventJS;
import com.gregtechceu.gtceu.integration.top.forge.TheOneProbePluginImpl;
import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.lowdraglib.gui.factory.UIFactory;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraftforge.client.event.RegisterPresetEditorsEvent;
import com.tterrag.registrate.providers.ProviderType;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLConstructModEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.javafmlmod.FMLModContainer;

public class CommonProxy {
    private static final Object LOCK = new Object();
    private static boolean isKubeJSSetup = false;

    public CommonProxy() {
        // used for forge events (ClientProxy + CommonProxy)
        IEventBus eventBus = FMLJavaModLoadingContext.get().getModEventBus();
        eventBus.register(this);
        eventBus.addListener(AlloyBlastPropertyAddition::addAlloyBlastProperties);
        // must be set here because of KubeJS compat
        // trying to read this before the pre-init stage
        GTCEuAPI.materialManager = MaterialRegistryManager.getInstance();

        GTRegistries.init(eventBus);
        GTFeatures.init(eventBus);
        // init common features
        if (GTCEu.isKubeJSLoaded()) {
            synchronized (LOCK) {
                if (!isKubeJSSetup) {
                    try { LOCK.wait(); } catch (InterruptedException ignored) {}
                }
            }
        }
        CommonProxy.init();
        GTRegistries.GLOBAL_LOOT_MODIFIES.register("tool", () -> ToolLootModifier.CODEC);
    }

    /**
     * If kjs is loaded, make sure our mod is loaded after it.
     */
    public static void onKubeJSSetup() {
        synchronized (LOCK) {
            isKubeJSSetup = true;
            LOCK.notify();
        }
    }

    public static void init() {
        GTCEu.LOGGER.info("GTCEu common proxy init!");
        ConfigHolder.init();
        GTCEuAPI.initializeHighTier();
        GTRegistries.COMPASS_NODES.unfreeze();

        UIFactory.register(MachineUIFactory.INSTANCE);
        UIFactory.register(CoverUIFactory.INSTANCE);
        UIFactory.register(GTUIEditorFactory.INSTANCE);
        GTPlacerTypes.init();
        GTRecipeCapabilities.init();
        GTRecipeConditions.init();
        GTElements.init();
        MaterialIconSet.init();
        MaterialIconType.init();
        initMaterials();
        TagPrefix.init();
        GTSoundEntries.init();
        GTDamageTypes.init();
        GTCompassSections.init();
        GTCompassNodes.init();
        GTCovers.init();
        GTFluids.init();
        GTCreativeModeTabs.init();
        GTBlocks.init();
        GTBlockEntities.init();
        GTRecipeTypes.init();
        GTMachines.init();
        GTFoods.init();
        GTItems.init();
        AddonFinder.getAddons().forEach(IGTAddon::initializeAddon);

        // fabric exclusive, squeeze this in here to register before stuff is used
        GTRegistration.REGISTRATE.registerRegistrate();

        // Register all material manager registries, for materials with mod ids.
        GTCEuAPI.materialManager.getRegistries().forEach(registry -> {
            // Register autogenerated data *before* builtin GT ones, because of overrides
            registry.getRegistrate().addDataGenerator(ProviderType.LANG, (provider) -> MaterialLangGenerator.generate(provider, registry));

            registry.getRegistrate()
                .registerEventListeners(ModList.get().getModContainerById(registry.getModid())
                    .filter(FMLModContainer.class::isInstance)
                    .map(FMLModContainer.class::cast)
                    .map(FMLModContainer::getEventBus)
                    .orElse(FMLJavaModLoadingContext.get().getModEventBus()));
        });

        GregTechDatagen.init();

        WorldGenLayers.registerAll();
        GTFeatures.init();
        StoneTypes.init();
        GTFeatures.register();
    }

    private static void initMaterials() {
        // First, register other mods' Registries
        MaterialRegistryManager managerInternal = (MaterialRegistryManager) GTCEuAPI.materialManager;

        GTCEu.LOGGER.info("Registering material registries");
        ModLoader.get().postEvent(new MaterialRegistryEvent());

        // First, register CEu Materials
        managerInternal.unfreezeRegistries();
        MaterialEvent materialEvent = new MaterialEvent();
        GTCEu.LOGGER.info("Registering GTCEu Materials");
        GTMaterials.init();
        MaterialRegistryManager.getInstance()
            .getRegistry(GTCEu.MOD_ID)
            .setFallbackMaterial(GTMaterials.Aluminium);

        // Then, register addon Materials
        GTCEu.LOGGER.info("Registering addon Materials");
        ModLoader.get().postEvent(materialEvent);
        AddonFinder.getAddons().forEach(IGTAddon::registerMaterials);
        if (GTCEu.isKubeJSLoaded()) {
            KJSEventWrapper.materialRegistry();
        }

        // Fire Post-Material event, intended for when Materials need to be iterated over in-full before freezing
        // Block entirely new Materials from being added in the Post event
        managerInternal.closeRegistries();
        ModLoader.get().postEvent(new PostMaterialEvent());
        if (GTCEu.isKubeJSLoaded()) {
            KJSEventWrapper.materialModification();
        }

        // Freeze Material Registry before processing Items, Blocks, and Fluids
        managerInternal.freezeRegistries();
        /* End Material Registration */
    }

    @SubscribeEvent
    public void modConstruct(FMLConstructModEvent event) {

    }

    @SubscribeEvent
    public void commonSetup(FMLCommonSetupEvent e) {
        e.enqueueWork(() -> {

        });
        CraftingHelper.register(SizedIngredient.TYPE, SizedIngredient.SERIALIZER);
    }

    @SubscribeEvent
    public void loadComplete(FMLLoadCompleteEvent e) {
        e.enqueueWork(() -> {
            if (LDLib.isModLoaded(GTValues.MODID_TOP)) {
                GTCEu.LOGGER.info("TheOneProbe found. Enabling integration...");
                TheOneProbePluginImpl.init();
            }
        });
    }

    @SubscribeEvent
    public void registerCapabilities(RegisterCapabilitiesEvent event) {
        GTCapability.register(event);
    }

    @SubscribeEvent
    public void registerPresetEditors(RegisterPresetEditorsEvent event) {
        event.register(GTFeatures.STRATA_PRESET, (screen, context) -> new CreateStrataWorldScreen(screen, (modifier) -> screen.getUiState().updateDimensions(strataConfigurator(modifier))));
    }

    private static WorldCreationContext.DimensionsUpdater strataConfigurator(StrataGenerationType type) {
        return (registryManager, arg3) -> {
            Registry<MultiNoiseBiomeSourceParameterList> parameterLists = registryManager.registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
            Registry<NoiseGeneratorSettings> registry = registryManager.registryOrThrow(Registries.NOISE_SETTINGS);
            Holder<NoiseGeneratorSettings> holder = switch (type) {
                case BLOB -> registry.getHolderOrThrow(GTFeatures.BLOB_STRATA_NOISE_SETTINGS);
                case LAYER -> registry.getHolderOrThrow(GTFeatures.LAYER_STRATA_NOISE_SETTINGS);
                case NONE -> registry.getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
            };
            MultiNoiseBiomeSource biomeSource = MultiNoiseBiomeSource.createFromPreset(parameterLists.getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
            ChunkGenerator chunkgenerator = new NoiseBasedChunkGenerator(biomeSource, holder);
            return arg3.replaceOverworldGenerator(registryManager, chunkgenerator);
        };
    }

    public static final class KJSEventWrapper {
        public static void materialRegistry() {
            GTRegistryInfo.registerFor(GTCEuAPI.materialManager.getRegistry(GTCEu.MOD_ID).getRegistryName());
        }

        public static void materialModification() {
            if (GTCEuStartupEvents.MATERIAL_MODIFICATION.hasListeners()) {
                GTCEuStartupEvents.MATERIAL_MODIFICATION.post(new MaterialModificationEventJS());
            }
        }
    }
}