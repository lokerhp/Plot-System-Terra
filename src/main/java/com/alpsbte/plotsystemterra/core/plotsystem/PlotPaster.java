package com.alpsbte.plotsystemterra.core.plotsystem;

import com.alpsbte.plotsystemterra.PlotSystemTerra;
import com.alpsbte.plotsystemterra.core.DatabaseConnection;
import com.alpsbte.plotsystemterra.core.api.PlotSystemAPI;
import com.alpsbte.plotsystemterra.core.api.RequestMethod;
import com.alpsbte.plotsystemterra.core.config.ConfigPaths;
import com.alpsbte.plotsystemterra.utils.FTPManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sk89q.worldedit.*;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class PlotPaster extends Thread {

    private final String serverName;

    public final boolean fastMode;
    private final int pasteInterval;
    public final World world;
    private final boolean broadcastMessages;
    private final boolean usesDB = PlotSystemTerra.getPlugin().usesDatabase();
    public PlotPaster() {
        FileConfiguration config = PlotSystemTerra.getPlugin().getConfig();

        this.serverName = config.getString(ConfigPaths.SERVER_NAME);
        this.fastMode = config.getBoolean(ConfigPaths.FAST_MODE);
        this.world = Bukkit.getWorld(config.getString(ConfigPaths.WORLD_NAME));
        this.pasteInterval = config.getInt(ConfigPaths.PASTING_INTERVAL);
        this.broadcastMessages = config.getBoolean(ConfigPaths.BROADCAST_INFO);
    }

    @Override
    public void run() {
        Bukkit.getScheduler().scheduleSyncRepeatingTask(PlotSystemTerra.getPlugin(), () -> {

            if (usesDB) {
                runTaskWithDB();
            } else {
                runTaskWithAPI();
            }
        }, 0L, 20L * pasteInterval);
    }

    private void runTaskWithDB(){
        try (ResultSet rs = DatabaseConnection.createStatement("SELECT id, city_project_id, mc_coordinates, version FROM plotsystem_plots WHERE status = 'completed' AND pasted = '0' LIMIT 20")
                .executeQuery()) {
            int pastedPlots = 0;

            if (rs.isBeforeFirst()) {
                while (rs.next()) {
                    int plotID = -1;
                    try {
                        plotID = rs.getInt(1);
                        CityProject city = new CityProject(rs.getInt(2));

                        try (ResultSet rsServer = DatabaseConnection.createStatement("SELECT name FROM plotsystem_servers WHERE id = ?")
                                .setValue(city.getServerID()).executeQuery()) {

                            if (rsServer.next()) {
                                String name = rsServer.getString(1);
                                if (name.equals(serverName)) {
                                    String[] splitCoordinates = rs.getString(3).split(",");

                                    Vector mcCoordinates = Vector.toBlockPoint(
                                            Float.parseFloat(splitCoordinates[0]),
                                            Float.parseFloat(splitCoordinates[1]),
                                            Float.parseFloat(splitCoordinates[2])
                                    );

                                    double version = rs.getDouble(4);
                                    if (rs.wasNull()) {
                                        version = 2;
                                    }

                                    pastePlotSchematic(plotID, city, world, mcCoordinates, version, fastMode);
                                    pastedPlots++;
                                }
                            }

                            DatabaseConnection.closeResultSet(rsServer);

                        }
                    } catch (Exception ex) {
                        Bukkit.getLogger().log(Level.SEVERE, "An error occurred while pasting plot #" + plotID + "!", ex);
                    }
                }

                if (broadcastMessages && pastedPlots != 0) {
                    Bukkit.broadcastMessage("§7§l>§a Pasted §6" + pastedPlots + " §aplot" + (pastedPlots > 1 ? "s" : "") + "!");
                }
            }

            DatabaseConnection.closeResultSet(rs);

        } catch (SQLException ex) {
            Bukkit.getLogger().log(Level.SEVERE, "A SQL error occurred!", ex);
        }
    }

    private void runTaskWithAPI(){
        try {
            JsonArray plots = (JsonArray) PlotSystemAPI.getInstance().getDataForPSUrl("teams/%API_KEY%/plots?status=completed&limit=20").getAsJsonArray();
            int pastedPlots = 0;
            while (plots.iterator().hasNext()) {
                JsonObject rs = plots.iterator().next().getAsJsonObject();
                int plotID = -1;
                try {
                    plotID = rs.get("id").getAsInt();
                    CityProject city = new CityProject(rs.get("city_project_id").getAsInt());

                    try {
                        JsonObject server = (JsonObject) PlotSystemAPI.getInstance().getDataForPSUrl("teams/%API_KEY%/servers/" + city.getServerID());
                        if (server.has("name")) {
                            String name = server.get("name").getAsString();
                            if (name.equals(serverName)) {
                                String[] splitCoordinates = rs.get("mc_coordinates").getAsString().split(",");

                                Vector mcCoordinates = Vector.toBlockPoint(
                                        Float.parseFloat(splitCoordinates[0]),
                                        Float.parseFloat(splitCoordinates[1]),
                                        Float.parseFloat(splitCoordinates[2])
                                );

                                double version;
                                if (rs.has("version")) {
                                    version = rs.get("version").getAsDouble();
                                } else {
                                    version = 2;
                                }

                                pastePlotSchematic(plotID, city, world, mcCoordinates, version, fastMode);
                                pastedPlots++;
                                if (broadcastMessages && pastedPlots != 0) {
                                    Bukkit.broadcastMessage("§7§l>§a Pasted §6" + pastedPlots + " §aplot" + (pastedPlots > 1 ? "s" : "") + "!");
                                }
                            }
                        }
                    } catch (Exception e) {
                        Bukkit.getLogger().log(Level.SEVERE, "An error occurred while pasting plot #" + plotID + "!", e);
                    }

                } catch (Exception ex) {
                    Bukkit.getLogger().log(Level.SEVERE, "A API error occurred!", ex);
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "A API error occurred!", e);
        }
    }
    public static void pastePlotSchematic(int plotID, CityProject city, World world, Vector mcCoordinates, double plotVersion, boolean fastMode) throws IOException, WorldEditException, SQLException, URISyntaxException {
        File outlineSchematic = Paths.get(PlotCreator.schematicsPath, String.valueOf(city.getServerID()), String.valueOf(city.getID()), plotID + ".schematic").toFile();
        File completedSchematic = Paths.get(PlotCreator.schematicsPath, String.valueOf(city.getServerID()), "finishedSchematics", String.valueOf(city.getID()), plotID + ".schematic").toFile();

        // Download from SFTP or FTP server if enabled
        FTPConfiguration ftpConfiguration = city.getFTPConfiguration();
        if (ftpConfiguration != null) {
            Files.deleteIfExists(completedSchematic.toPath());
            FTPManager.downloadSchematic(FTPManager.getFTPUrl(ftpConfiguration, city.getID()), completedSchematic);
        }

        if (outlineSchematic.exists() && completedSchematic.exists()) {
            com.sk89q.worldedit.world.World weWorld = new BukkitWorld(world);
            EditSession editSession = WorldEdit.getInstance().getEditSessionFactory().getEditSession(weWorld, -1);
            if (fastMode) editSession.setFastMode(true);
            editSession.enableQueue();

            Clipboard outlineClipboard = ClipboardFormat.SCHEMATIC.getReader(Files.newInputStream(outlineSchematic.toPath())).read(weWorld.getWorldData());
            Clipboard completedClipboard = ClipboardFormat.SCHEMATIC.getReader(Files.newInputStream(completedSchematic.toPath())).read(weWorld.getWorldData());

            Vector toPaste;
            if (plotVersion >= 3) {
                Vector plotOriginOutline = outlineClipboard.getOrigin();
                toPaste = new Vector(plotOriginOutline.getX(), plotOriginOutline.getY(), plotOriginOutline.getZ());
            } else toPaste = mcCoordinates;

            Operation operation = new ClipboardHolder(completedClipboard, weWorld.getWorldData()).createPaste(editSession, weWorld.getWorldData())
                    .to(toPaste).ignoreAirBlocks(true).build();
            Operations.complete(operation);
            editSession.flushQueue();

            if(PlotSystemTerra.getPlugin().usesDatabase()) {
                DatabaseConnection.createStatement("UPDATE plotsystem_plots SET pasted = '1' WHERE id = ?")
                        .setValue(plotID).executeUpdate();
            } else {
                Map<String, Object> requestBodyMap = new HashMap<>();
                requestBodyMap.put("pasted", "true");

                try {
                    if(PlotSystemAPI.getInstance().makeRequest("teams/%API_KEY%/plot/" + plotID + "", RequestMethod.PUT, requestBodyMap).getResponseCode() == 200){
                        Bukkit.getLogger().log(Level.INFO, "Successfully updated plot #" + plotID + "!");
                    } else {
                        Bukkit.getLogger().log(Level.WARNING, "Failed to update plot #" + plotID + "!");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else {
            Bukkit.getLogger().log(Level.WARNING, "Could not find schematic file(s) of plot #" + plotID + "!");
        }
    }
}
