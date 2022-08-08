package me.mykindos.betterpvp.clans.clans.map.renderer;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.Getter;
import me.mykindos.betterpvp.clans.Clans;
import me.mykindos.betterpvp.clans.clans.map.MapHandler;
import me.mykindos.betterpvp.clans.clans.map.data.Coords;
import me.mykindos.betterpvp.clans.clans.map.data.ExtraCursor;
import me.mykindos.betterpvp.clans.clans.map.data.MapPixel;
import me.mykindos.betterpvp.clans.clans.map.data.MapSettings;
import me.mykindos.betterpvp.clans.clans.map.events.MinimapExtraCursorEvent;
import me.mykindos.betterpvp.clans.clans.map.nms.UtilMapMaterial;
import me.mykindos.betterpvp.core.config.Config;
import me.mykindos.betterpvp.core.utilities.UtilServer;
import net.minecraft.world.level.material.MaterialColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.map.*;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;

@Getter
@Singleton
public class MinimapRenderer extends MapRenderer implements Listener {

    private final MapHandler mapHandler;
    protected Map<String, Map<Integer, Map<Integer, MapPixel>>> worldCacheMap = new TreeMap<>();
    protected Queue<Coords> queue = new LinkedList<>();

    @Inject
    @Config(path = "clans.map.maxProcess", defaultValue = "64")
    private int maxProcess;


    @Inject
    public MinimapRenderer(MapHandler mapHandler, Clans clans) {
        super(true);
        this.mapHandler = mapHandler;
        Bukkit.getPluginManager().registerEvents(this, clans);
        UtilServer.runTaskTimer(clans, this::processQueue, 5, 5);
    }

    private void processQueue() {
        if (queue.isEmpty()) {
            return;
        }

        for (int i = 0; i < maxProcess; i++) {
            final Coords poll = queue.poll();

            if (poll == null) {
                return;
            }

            World world = Bukkit.getWorld(poll.getWorld());

            if(world == null) continue;
            if (!getWorldCacheMap().containsKey(poll.getWorld())) continue;
            if (!getWorldCacheMap().get(poll.getWorld()).containsKey(poll.getX())) continue;
            if (!getWorldCacheMap().get(poll.getWorld()).get(poll.getX()).containsKey(poll.getZ())) continue;

            Block block = world.getBlockAt(poll.getX(), world.getHighestBlockYAt(poll.getX(), poll.getZ()), poll.getZ());
            if (!block.getChunk().isLoaded()) continue;

            while (block.getY() > 0 && UtilMapMaterial.getBlockColor(block) == UtilMapMaterial.getColorNeutral()) {
                block = world.getBlockAt(block.getX(), block.getY() - 1, block.getZ());
            }
            short avgY = 0;
            avgY += block.getY();

            var mainColor = UtilMapMaterial.getBlockColor(block);

            final MapPixel mapPixel = getWorldCacheMap().get(block.getWorld().getName()).get(block.getX()).get(block.getZ());
            mapPixel.setAverageY(avgY);
            mapPixel.setColorId(mainColor.id);
        }
    }


    @SuppressWarnings("deprecation")
    @Override
    public void render(@NotNull MapView map, @NotNull MapCanvas canvas, @NotNull Player player) {
        if (player.getInventory().getItemInMainHand().getType() != Material.FILLED_MAP) return;

        int centerX = player.getLocation().getBlockX();
        int centerZ = player.getLocation().getBlockZ();

        final MapSettings mapSettings = mapHandler.mapSettingsMap.get(player.getUniqueId());

        int scale = 1 << mapSettings.getScale().getValue();

        if (mapSettings.getScale() == MapSettings.Scale.FAR) {
            centerX = 0;
            centerZ = 0;
        }

        if (!worldCacheMap.containsKey(player.getWorld().getName()))
            worldCacheMap.put(player.getWorld().getName(), new TreeMap<>());

        final Map<Integer, Map<Integer, MapPixel>> cacheMap = worldCacheMap.get(player.getWorld().getName());

        final boolean hasMoved = mapHandler.hasMoved(player);

        if (hasMoved || mapSettings.isUpdate()) {
            for (int i = 0; i < 128; i++) {
                for (int j = 0; j < 128; j++) {
                    canvas.setPixelColor(i, j, Color.WHITE);
                }
            }
            int locX = centerX / scale - 64;
            int locZ = centerZ / scale - 64;
            for (int i = 0; i < 128; i++) {
                for (int j = 0; j < 128; j++) {
                    int x = (locX + i) * scale;
                    int z = (locZ + j) * scale;
//
                    if (locX + i < 0 && (locX + i) % scale != 0)
                        x--;
                    if (locZ + j < 0 && (locZ + j) % scale != 0)
                        z--;


                    var pixelX = cacheMap.get(x);
                    if (pixelX != null && pixelX.containsKey(z)) {
                        final MapPixel mapPixel = pixelX.get(z);
                        short prevY = getPrevY(x, z, player.getWorld().getName(), scale);
//
                        double d2 = (mapPixel.getAverageY() - prevY) * 4.0D / (scale + 4) + ((i + j & 1) - 0.5D) * 0.4D;

                        MaterialColor.Brightness brightness = MaterialColor.Brightness.NORMAL;

                        if (d2 > 0.6D) {
                            brightness = MaterialColor.Brightness.HIGH;
                        }else if (d2 < -0.6D) {
                            brightness = MaterialColor.Brightness.LOW;
                        }

                        MaterialColor materialColor = MaterialColor.byId(mapPixel.getColorId());
                        canvas.setPixel(i, j, materialColor.getPackedId(brightness));
                    } else {
                        for (int k = -scale; k < scale; k++) {
                            for (int l = -scale; l < scale; l++) {
                                handlePixel(cacheMap, x + k, z + l, player);
                            }
                        }
                    }
                }
            }
        }
        handleCursors(canvas, player, scale, centerX, centerZ);
    }

    private void addToQueue(Coords coords) {
        if (!queue.contains(coords)) {
            queue.add(coords);
        }
    }

    private void handlePixel(Map<Integer, Map<Integer, MapPixel>> cacheMap, int x, int z, Player player) {
        if (!cacheMap.containsKey(x)) {
            cacheMap.put(x, new TreeMap<>());
        }
        if (!cacheMap.get(x).containsKey(z)) {

            int y = player.getWorld().getHighestBlockYAt(x, z);

            Block block = player.getWorld().getBlockAt(x, y, z);

            if (!block.getChunk().isLoaded()) {
                return;
            }
            while (block.getY() > 0 && UtilMapMaterial.getBlockColor(block) == UtilMapMaterial.getColorNeutral()) {
                block = player.getWorld().getBlockAt(block.getX(), block.getY() - 1, block.getZ());
            }
            short avgY = 0;
            avgY += block.getY();

            var mainColor = UtilMapMaterial.getBlockColor(block).id;

            cacheMap.get(x).put(z, new MapPixel(mainColor, avgY));
        }
    }

    private void handleCursors(MapCanvas canvas, Player player, int scale, int centerX, int centerZ) {
        MapCursorCollection cursors = canvas.getCursors();
        while (cursors.size() > 0) {
            cursors.removeCursor(cursors.getCursor(0));
        }

        MinimapExtraCursorEvent cursorEvent = UtilServer.callEvent(new MinimapExtraCursorEvent(player, cursors, scale));
        for (ExtraCursor cursor : cursorEvent.getCursors()) {
            if (!cursor.getWorld().equalsIgnoreCase(player.getWorld().getName())) {
                continue;
            }

            int x = ((cursor.getX() - centerX) / scale) * 2;
            int z = ((cursor.getZ() - centerZ) / scale) * 2;

            if (Math.abs(x) > 127) {
                if (cursor.isShownOutside()) {
                    x = cursor.getX() > player.getLocation().getBlockX() ? 127 : -128;
                } else {
                    continue;
                }
            }

            if (Math.abs(z) > 127) {
                if (cursor.isShownOutside()) {
                    z = cursor.getZ() > player.getLocation().getBlockZ() ? 127 : -128;
                } else {
                    continue;
                }
            }
            MapCursor mapCursor = new MapCursor((byte) x, (byte) z, cursor.getDirection(), cursor.getType(), cursor.isVisible());
            cursors.addCursor(mapCursor);
        }
    }

    private short getPrevY(int x, int z, String world, int scale) {
        final Map<Integer, Map<Integer, MapPixel>> cacheMap = worldCacheMap.get(world);

        if (cacheMap.containsKey(x - scale) && cacheMap.get(x - scale).containsKey(z - scale)) {
            return cacheMap.get(x - scale).get(z - scale).getAverageY();
        }
        if (cacheMap.containsKey(x + scale) && cacheMap.get(x + scale).containsKey(z + scale)) {
            return cacheMap.get(x + scale).get(z + scale).getAverageY();
        }
        if (cacheMap.containsKey(x - scale) && cacheMap.get(x - scale).containsKey(z + scale)) {
            return cacheMap.get(x - scale).get(z + scale).getAverageY();
        }
        if (cacheMap.containsKey(x - scale) && cacheMap.get(x + -scale).containsKey(z + scale)) {
            return cacheMap.get(x - scale).get(z + scale).getAverageY();
        }
        return 0;
    }

    private void handleBlockEvent(Block block) {
        addToQueue(new Coords(block.getX(), block.getZ(), block.getWorld().getName()));
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockEvent(BlockPlaceEvent event) {
        handleBlockEvent(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockEvent(BlockFromToEvent event) {
        handleBlockEvent(event.getBlock());
        handleBlockEvent(event.getToBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockEvent(BlockPhysicsEvent event) {
        switch (event.getChangedType()) {
            case LAVA, WATER -> handleBlockEvent(event.getBlock());
            default -> {}
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockEvent(BlockBreakEvent event) {
        handleBlockEvent(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockEvent(BlockBurnEvent event) {
        handleBlockEvent(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockEvent(BlockFadeEvent event) {
        handleBlockEvent(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockEvent(BlockFormEvent event) {
        handleBlockEvent(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockEvent(BlockGrowEvent event) {
        handleBlockEvent(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockEvent(BlockSpreadEvent event) {
        handleBlockEvent(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockEvent(EntityBlockFormEvent event) {
        handleBlockEvent(event.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {

    }
}
