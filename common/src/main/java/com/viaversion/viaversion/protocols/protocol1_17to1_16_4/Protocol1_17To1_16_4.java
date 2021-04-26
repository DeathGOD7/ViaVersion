/*
 * This file is part of ViaVersion - https://github.com/ViaVersion/ViaVersion
 * Copyright (C) 2016-2021 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.viaversion.viaversion.protocols.protocol1_17to1_16_4;

import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.data.MappingData;
import com.viaversion.viaversion.api.protocol.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.packet.ClientboundPacketType;
import com.viaversion.viaversion.api.protocol.Protocol;
import com.viaversion.viaversion.api.protocol.remapper.PacketRemapper;
import com.viaversion.viaversion.rewriter.MetadataRewriter;
import com.viaversion.viaversion.rewriter.RegistryType;
import com.viaversion.viaversion.rewriter.SoundRewriter;
import com.viaversion.viaversion.rewriter.StatisticsRewriter;
import com.viaversion.viaversion.rewriter.TagRewriter;
import com.viaversion.viaversion.api.type.Type;
import com.viaversion.viaversion.protocols.protocol1_16_2to1_16_1.ClientboundPackets1_16_2;
import com.viaversion.viaversion.protocols.protocol1_16_2to1_16_1.ServerboundPackets1_16_2;
import com.viaversion.viaversion.protocols.protocol1_17to1_16_4.metadata.MetadataRewriter1_17To1_16_4;
import com.viaversion.viaversion.protocols.protocol1_17to1_16_4.packets.EntityPackets;
import com.viaversion.viaversion.protocols.protocol1_17to1_16_4.packets.InventoryPackets;
import com.viaversion.viaversion.protocols.protocol1_17to1_16_4.packets.WorldPackets;
import com.viaversion.viaversion.protocols.protocol1_17to1_16_4.storage.BiomeStorage;
import com.viaversion.viaversion.protocols.protocol1_17to1_16_4.storage.EntityTracker1_17;

public class Protocol1_17To1_16_4 extends Protocol<ClientboundPackets1_16_2, ClientboundPackets1_17, ServerboundPackets1_16_2, ServerboundPackets1_17> {

    public static final MappingData MAPPINGS = new MappingData("1.16.2", "1.17", true);
    private static final String[] NEW_GAME_EVENT_TAGS = {"minecraft:ignore_vibrations_sneaking", "minecraft:vibrations"};
    private TagRewriter tagRewriter;

    public Protocol1_17To1_16_4() {
        super(ClientboundPackets1_16_2.class, ClientboundPackets1_17.class, ServerboundPackets1_16_2.class, ServerboundPackets1_17.class);
    }

    @Override
    protected void registerPackets() {
        MetadataRewriter metadataRewriter = new MetadataRewriter1_17To1_16_4(this);

        EntityPackets.register(this);
        InventoryPackets.register(this);
        WorldPackets.register(this);

        tagRewriter = new TagRewriter(this, null);
        registerOutgoing(ClientboundPackets1_16_2.TAGS, new PacketRemapper() {
            @Override
            public void registerMap() {
                handler(wrapper -> {
                    // Tags are now generically written with resource location - 5 different Vanilla types
                    wrapper.write(Type.VAR_INT, 5);
                    for (RegistryType type : RegistryType.getValues()) {
                        // Prefix with resource location
                        wrapper.write(Type.STRING, type.getResourceLocation());

                        // Id conversion
                        tagRewriter.handle(wrapper, tagRewriter.getRewriter(type), tagRewriter.getNewTags(type));

                        // Stop iterating after entity types
                        if (type == RegistryType.ENTITY) {
                            break;
                        }
                    }

                    // New Game Event tags type
                    wrapper.write(Type.STRING, RegistryType.GAME_EVENT.getResourceLocation());
                    wrapper.write(Type.VAR_INT, NEW_GAME_EVENT_TAGS.length);
                    for (String tag : NEW_GAME_EVENT_TAGS) {
                        wrapper.write(Type.STRING, tag);
                        wrapper.write(Type.VAR_INT_ARRAY_PRIMITIVE, new int[0]);
                    }
                });
            }
        });

        new StatisticsRewriter(this, metadataRewriter::getNewEntityId).register(ClientboundPackets1_16_2.STATISTICS);

        SoundRewriter soundRewriter = new SoundRewriter(this);
        soundRewriter.registerSound(ClientboundPackets1_16_2.SOUND);
        soundRewriter.registerSound(ClientboundPackets1_16_2.ENTITY_SOUND);

        registerOutgoing(ClientboundPackets1_16_2.RESOURCE_PACK, new PacketRemapper() {
            @Override
            public void registerMap() {
                handler(wrapper -> {
                    wrapper.passthrough(Type.STRING);
                    wrapper.passthrough(Type.STRING);
                    wrapper.write(Type.BOOLEAN, Via.getConfig().isForcedUse1_17ResourcePack()); // Required
                    wrapper.write(Type.OPTIONAL_COMPONENT, null); // Prompt message
                });
            }
        });

        registerOutgoing(ClientboundPackets1_16_2.MAP_DATA, new PacketRemapper() {
            @Override
            public void registerMap() {
                handler(wrapper -> {
                    wrapper.passthrough(Type.VAR_INT);
                    wrapper.passthrough(Type.BYTE);
                    wrapper.read(Type.BOOLEAN); // Tracking position removed
                    wrapper.passthrough(Type.BOOLEAN);

                    int size = wrapper.read(Type.VAR_INT);
                    // Write whether markers exists or not
                    if (size != 0) {
                        wrapper.write(Type.BOOLEAN, true);
                        wrapper.write(Type.VAR_INT, size);
                    } else {
                        wrapper.write(Type.BOOLEAN, false);
                    }
                });
            }
        });

        registerOutgoing(ClientboundPackets1_16_2.TITLE, null, new PacketRemapper() {
            @Override
            public void registerMap() {
                handler(wrapper -> {
                    // Title packet actions have been split into individual packets (the content hasn't changed)
                    int type = wrapper.read(Type.VAR_INT);
                    ClientboundPacketType packetType;
                    switch (type) {
                        case 0:
                            packetType = ClientboundPackets1_17.TITLE_TEXT;
                            break;
                        case 1:
                            packetType = ClientboundPackets1_17.TITLE_SUBTITLE;
                            break;
                        case 2:
                            packetType = ClientboundPackets1_17.ACTIONBAR;
                            break;
                        case 3:
                            packetType = ClientboundPackets1_17.TITLE_TIMES;
                            break;
                        case 4:
                            packetType = ClientboundPackets1_17.CLEAR_TITLES;
                            wrapper.write(Type.BOOLEAN, false); // Reset times
                            break;
                        case 5:
                            packetType = ClientboundPackets1_17.CLEAR_TITLES;
                            wrapper.write(Type.BOOLEAN, true); // Reset times
                            break;
                        default:
                            throw new IllegalArgumentException("Invalid title type received: " + type);
                    }

                    wrapper.setId(packetType.ordinal());
                });
            }
        });

        registerOutgoing(ClientboundPackets1_16_2.EXPLOSION, new PacketRemapper() {
            @Override
            public void registerMap() {
                map(Type.FLOAT); // X
                map(Type.FLOAT); // Y
                map(Type.FLOAT); // Z
                map(Type.FLOAT); // Strength
                handler(wrapper -> {
                    // Collection length is now a var int
                    wrapper.write(Type.VAR_INT, wrapper.read(Type.INT));
                });
            }
        });

        registerOutgoing(ClientboundPackets1_16_2.SPAWN_POSITION, new PacketRemapper() {
            @Override
            public void registerMap() {
                map(Type.POSITION1_14);
                handler(wrapper -> {
                    // Angle (which Mojang just forgot to write to the buffer, lol)
                    wrapper.write(Type.FLOAT, 0f);
                });
            }
        });

        registerIncoming(ServerboundPackets1_17.CLIENT_SETTINGS, new PacketRemapper() {
            @Override
            public void registerMap() {
                map(Type.STRING); // Locale
                map(Type.BYTE); // View distance
                map(Type.VAR_INT); // Chat mode
                map(Type.BOOLEAN); // Chat colors
                map(Type.UNSIGNED_BYTE); // Chat flags
                map(Type.VAR_INT); // Main hand
                handler(wrapper -> {
                    wrapper.read(Type.BOOLEAN); // Text filtering
                });
            }
        });
    }

    @Override
    protected void onMappingDataLoaded() {
        tagRewriter.addEmptyTags(RegistryType.ITEM, "minecraft:candles", "minecraft:ignored_by_piglin_babies", "minecraft:piglin_food", "minecraft:freeze_immune_wearables",
                "minecraft:axolotl_tempt_items", "minecraft:occludes_vibration_signals",
                "minecraft:diamond_ores", "minecraft:iron_ores", "minecraft:lapis_ores", "minecraft:redstone_ores",
                "minecraft:coal_ores", "minecraft:copper_ores", "minecraft:emerald_ores", "minecraft:cluster_max_harvestables");
        tagRewriter.addEmptyTags(RegistryType.BLOCK, "minecraft:crystal_sound_blocks", "minecraft:candle_cakes", "minecraft:candles",
                "minecraft:snow_step_sound_blocks", "minecraft:inside_step_sound_blocks", "minecraft:occludes_vibration_signals", "minecraft:dripstone_replaceable_blocks",
                "minecraft:cave_vines", "minecraft:moss_replaceable", "minecraft:deepslate_ore_replaceables", "minecraft:lush_ground_replaceable",
                "minecraft:diamond_ores", "minecraft:iron_ores", "minecraft:lapis_ores", "minecraft:redstone_ores", "minecraft:stone_ore_replaceables",
                "minecraft:coal_ores", "minecraft:copper_ores", "minecraft:emerald_ores", "minecraft:dirt", "minecraft:snow");
        tagRewriter.addEmptyTags(RegistryType.ENTITY, "minecraft:powder_snow_walkable_mobs", "minecraft:axolotl_always_hostiles", "minecraft:axolotl_tempted_hostiles",
                "minecraft:axolotl_hunt_targets", "minecraft:freeze_hurts_extra_types", "minecraft:freeze_immune_entity_types");

        tagRewriter.addTag(RegistryType.BLOCK, "minecraft:cauldrons", 261);
        tagRewriter.addTag(RegistryType.ITEM, "minecraft:fox_food", 948);
    }

    @Override
    public void init(UserConnection user) {
        user.put(new BiomeStorage(user));
        user.put(new EntityTracker1_17(user));
    }

    @Override
    public MappingData getMappingData() {
        return MAPPINGS;
    }
}