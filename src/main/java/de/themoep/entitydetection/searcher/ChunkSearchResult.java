package de.themoep.entitydetection.searcher;

import de.themoep.entitydetection.ChunkLocation;
import de.themoep.entitydetection.EntityDetection;
import de.themoep.entitydetection.Utils;
import io.papermc.lib.PaperLib;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

public class ChunkSearchResult extends SearchResult<ChunkLocation> {
    public ChunkSearchResult(EntitySearch search, EntityDetection plugin) {
        super(search, plugin);
    }

    @Override
    public void addEntity(Entity entity) {
        add(entity.getLocation(), entity.getType().toString());
    }

    @Override
    public void addBlockState(BlockState blockState) {
        add(blockState.getLocation(), blockState.getType().toString());
    }

    @Override
    public void add(Location location, String type) {
        ChunkLocation chunkLocation = new ChunkLocation(location);

        if (!resultEntryMap.containsKey(chunkLocation)) {
            resultEntryMap.put(chunkLocation, new SearchResultEntry<>(chunkLocation));
        }
        resultEntryMap.get(chunkLocation).increment(type);
    }

    @Override
    public void teleport(Player sender, SearchResultEntry<ChunkLocation> entry, int i) {
        Chunk chunk = entry.getLocation().toBukkit(Bukkit.getServer());
        Bukkit.getScheduler().runTask(getPlugin(), () -> chunk.load()); // Load chunk on main thread
        if (PaperLib.isChunkGenerated(chunk.getWorld(), chunk.getX(), chunk.getZ())) {
            PaperLib.getChunkAtAsync(chunk.getWorld(), chunk.getX(), chunk.getZ()).thenAcceptAsync(loadedChunk -> {
                Location loc = null;
                for (Entity e : loadedChunk.getEntities()) {
                    if (e.getType().toString().equals(entry.getEntryCount().get(0).getKey())) {
                        loc = e.getLocation();
                        break;
                    }
                }
                for (BlockState b : loadedChunk.getTileEntities()) {
                    if (b.getType().toString().equals(entry.getEntryCount().get(0).getKey())) {
                        loc = b.getLocation().add(0, 1, 0);
                        break;
                    }
                }
                if (loc == null) {
                    loc = loadedChunk.getWorld().getHighestBlockAt(chunk.getX() << 4 + 8, chunk.getZ() << 4 + 8).getLocation().add(0, 2, 0);
                }
                final Location location = loc;
                Bukkit.getScheduler().runTaskLaterAsynchronously(getPlugin(), () -> {
                    try {
                        sender.teleport(location, PlayerTeleportEvent.TeleportCause.PLUGIN);
                        sender.sendMessage(ChatColor.GREEN + "Teleported to entry " + ChatColor.WHITE + i + ": " +
                                ChatColor.YELLOW + entry.getLocation() + " " + ChatColor.RED + entry.getSize() + " " +
                                ChatColor.GREEN + Utils.enumToHumanName(entry.getEntryCount().get(0).getKey()) + "[" +
                                ChatColor.WHITE + entry.getEntryCount().get(0).getValue() + ChatColor.GREEN + "]");
                    } catch (IllegalArgumentException e) {
                        sender.sendMessage(ChatColor.RED + "Error during teleportation: " + e.getMessage());
                    }
                }, 1);
            }).exceptionally(e -> {
                e.printStackTrace();
                return null;
            });
        }
    }


}
