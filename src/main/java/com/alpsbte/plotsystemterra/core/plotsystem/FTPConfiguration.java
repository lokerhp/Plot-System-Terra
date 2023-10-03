package com.alpsbte.plotsystemterra.core.plotsystem;

import com.alpsbte.plotsystemterra.PlotSystemTerra;
import com.alpsbte.plotsystemterra.core.DatabaseConnection;
import com.alpsbte.plotsystemterra.core.api.PlotSystemAPI;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;

public class FTPConfiguration {
    private int ID;

    private String schematicPath;
    private String address;
    private int port;
    private boolean isSFTP;
    private String username;
    private String password;

    public FTPConfiguration(int ID) {
        if(PlotSystemTerra.getPlugin().usesDatabase()) {
            try {
                this.ID = ID;
                try (ResultSet rs = DatabaseConnection.createStatement("SELECT schematics_path, address, port, isSFTP, username, password FROM plotsystem_ftp_configurations WHERE id = ?")
                        .setValue(this.ID).executeQuery()) {

                    if (rs.next()) {
                        this.schematicPath = rs.getString(1);
                        this.address = rs.getString(2);
                        this.port = rs.getInt(3);
                        this.isSFTP = rs.getBoolean(4);
                        this.username = rs.getString(5);
                        this.password = rs.getString(6);
                    }

                    DatabaseConnection.closeResultSet(rs);

                }
            } catch (SQLException ex) {
                Bukkit.getLogger().log(Level.SEVERE, "A SQL error occurred!", ex);
                ex.printStackTrace();
            }
        } else {
            try {
                JsonObject object = PlotSystemAPI.getInstance().getDataForURL("teams/%API_KEY%/ftp_configuration");
                this.address = object.get("address").toString();
                this.port = Integer.parseInt(object.get("port").toString());
                this.isSFTP = Boolean.parseBoolean(object.get("isSFTP").toString());
                this.username = object.get("username").toString();
                this.password = object.get("password").toString();
                this.schematicPath = object.get("schematics_path").toString();

            } catch (Exception e) {
                Bukkit.getLogger().log(Level.SEVERE, "Failed to load FTP configuration from API!", e);
                e.printStackTrace();
            }
        }
    }

    public int getID() {
        return ID;
    }

    public String getSchematicPath() {
        if (schematicPath != null) {
            schematicPath = schematicPath.startsWith("/") ? schematicPath.substring(1, schematicPath.length()) : schematicPath;
            schematicPath = schematicPath.endsWith("/") ? schematicPath.substring(0, schematicPath.length() - 1) : schematicPath;
        }
        return schematicPath;
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public boolean isSFTP() {
        return isSFTP;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
