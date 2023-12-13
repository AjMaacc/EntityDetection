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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

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

    // Method to find teleport location within the chunk
    private CompletableFuture<Location> findTeleportLocation(Chunk chunk, SearchResultEntry<ChunkLocation> entry) {
        CompletableFuture<Location> locationFuture = new CompletableFuture<>();
        if (!chunk.isLoaded())
            chunk.load();
        // Asynchronous processing using PaperLib
        PaperLib.getChunkAtAsync(chunk.getWorld(), chunk.getX(), chunk.getZ()).thenAccept(loadedChunk -> {
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
                loc = loadedChunk.getWorld().getHighestBlockAt(loadedChunk.getX() << 4 + 8, loadedChunk.getZ() << 4 + 8).getLocation().add(0, 2, 0);
            }
            locationFuture.complete(loc);
        }).exceptionally(ex -> {
            locationFuture.completeExceptionally(ex);
            return null;
        });

        return locationFuture;
    }


    // Method to send a success message after teleportation
    private void sendTeleportMessage(Player sender, SearchResultEntry<ChunkLocation> entry, int i) {
        sender.sendMessage(ChatColor.GREEN + "Teleported to entry " + ChatColor.WHITE + i + ": " +
                ChatColor.YELLOW + entry.getLocation() + " " + ChatColor.RED + entry.getSize() + " " +
                ChatColor.GREEN + Utils.enumToHumanName(entry.getEntryCount().get(0).getKey()) + "[" +
                ChatColor.WHITE + entry.getEntryCount().get(0).getValue() + ChatColor.GREEN + "]");
    }
    @Override
    public void teleport(Player sender, SearchResultEntry<ChunkLocation> entry, int i) {
        entry.getLocation().toBukkitAsync(Bukkit.getServer()).thenAccept(chunk -> {
            chunk.load();
            if (chunk.isLoaded()) {
                findTeleportLocation(chunk, entry).thenAccept(loc -> {
                    if (loc != null && loc.getWorld() != null) {
                        PaperLib.teleportAsync(sender, loc, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(result -> {
                            if (Boolean.TRUE.equals(result)) {
                                sendTeleportMessage(sender, entry, i);
                            }
                        });
                    } else {
                        sender.sendMessage(ChatColor.RED + "No valid location found for teleportation.");
                    }
                });
            } else {
                sender.sendMessage(ChatColor.RED + "Chunk is not loaded or world data is null.");
            }
        }).exceptionally(e -> {
            sender.sendMessage(ChatColor.RED + "Error during teleportation: " + e.getMessage());
            return null;
        });
    }



}
