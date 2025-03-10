package me.greysilly7.npcsmadeasy;

import com.github.juliarn.npclib.api.Platform;
import com.github.juliarn.npclib.api.Position;
import com.github.juliarn.npclib.api.event.InteractNpcEvent;
import com.github.juliarn.npclib.api.event.ShowNpcEvent;
import com.github.juliarn.npclib.api.profile.Profile;
import com.github.juliarn.npclib.api.profile.ProfileProperty;
import com.github.juliarn.npclib.fabric.FabricPlatform;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import me.greysilly7.npcsmadeasy.config.Config;
import me.greysilly7.npcsmadeasy.config.MineSkin;
import me.greysilly7.npcsmadeasy.config.NPC;
import me.greysilly7.npcsmadeasy.util.MojangSkinGenerator;
import me.greysilly7.npcsmadeasy.util.mineskin.MineSkinResponse;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

public class NPCMadeEasyMod implements ModInitializer {
    public static final String MOD_ID = "npcs_made_easy";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Config CONFIG = new Config();

    private final @NotNull Platform<ServerWorld, ServerPlayerEntity, ItemStack, ?> platform = FabricPlatform
            .fabricNpcPlatformBuilder()
            .extension(this)
            .actionController(builder -> {
            })
            .build();

    @Override
    public void onInitialize() {
        Path configPath = getConfigPath();

        try {
            CONFIG.loadConfig(configPath);
        } catch (Exception e) {
            LOGGER.error("Failed to load config from {}: {}", configPath, e.getMessage());
            return;
        }

        registerPayloads();
        registerWorldLoadListener(CONFIG);
    }

    private Path getConfigPath() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        return configDir.resolve("npcs_made_easy.json");
    }

    private void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(ServerSwitchPayload.ID, ServerSwitchPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ServerSwitchPayload.ID, ServerSwitchPayload.CODEC);
    }

    private void registerWorldLoadListener(Config config) {
        ServerWorldEvents.LOAD.register((server, world) -> {
            if (world.getRegistryKey() == World.OVERWORLD) {
                spawnNpcs(config, world);
                registerNPCListeners(config);
            }
        });
    }

    private void spawnNpcs(Config config, ServerWorld world) {
        MineSkin mineSkin = NPCMadeEasyMod.CONFIG.getMineSkin();

        for (NPC npc : config.getNpcs()) {
            LOGGER.info("Spawning NPC {}", npc.name());
            var npcbuilder = platform.newNpcBuilder()
                    .position(Position.position(npc.position().x(), npc.position().y(), npc.position().z(),
                            world.getRegistryKey().getValue().toString()));

            if (mineSkin != null && mineSkin.enable()) {
                var skin = MojangSkinGenerator.fetchFromUUID(npc.name());
                if (skin instanceof MineSkinResponse) {
                    MineSkinResponse mineSkinResponse = (MineSkinResponse) skin;

                    npcbuilder
                            .profile(Profile.resolved(mineSkinResponse.name(), UUID.fromString(mineSkinResponse.uuid()),
                                    Set.of(ProfileProperty.property("textures",
                                            mineSkinResponse.texture().data().value(),
                                            mineSkinResponse.texture().data().signature()))));
                } else {
                    npcbuilder.profile(Profile.unresolved(npc.name()));
                }
            } else {
                npcbuilder.profile(Profile.unresolved(npc.name()));
            }

            npcbuilder.profile(Profile.unresolved(npc.name()));
            npcbuilder.buildAndTrack();
        }

    }

    private void registerNPCListeners(Config config) {
        var eventManager = this.platform.eventManager();
        eventManager.registerEventHandler(InteractNpcEvent.class,
                interactNpcEvent -> handleNpcInteraction(interactNpcEvent, config));
        eventManager.registerEventHandler(ShowNpcEvent.Post.class, showEvent -> handleNpcShow(showEvent, config));
    }

    private void handleNpcInteraction(InteractNpcEvent interactNpcEvent, Config config) {
        var npc = interactNpcEvent.npc();
        ServerPlayerEntity player = interactNpcEvent.player();
        config.getNpcByName(npc.profile().name()).ifPresent(npcConfig -> {
            String server = npcConfig.serverToFowardPlayerTo();
            // ServerPlayNetworking.send(player, new ServerSwitchPayload("Connect",
            // server));

            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(server);
            ServerPlayNetworking.send(player, new ServerSwitchPayload(out.toByteArray()));
        });
    }

    private void handleNpcShow(ShowNpcEvent.Post showEvent, Config config) {
        var npc = showEvent.npc();
        ServerPlayerEntity player = showEvent.player();
        config.getNpcByName(npc.profile().name()).ifPresent(npcConfig -> {
            npc.lookAt(Position.position(0.564, 17, 0.337, npc.position().worldId())).schedule(player);
            npc.rotate(npcConfig.yaw(), npcConfig.pitch()).schedule(player);
            ;
        });
    }
}