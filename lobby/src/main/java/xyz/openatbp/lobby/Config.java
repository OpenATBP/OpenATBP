package xyz.openatbp.lobby;

import java.io.*;
import java.util.Properties;

public class Config {
    private static Properties props;

    public static Boolean loadConfig(){
        try (InputStream input = new FileInputStream("config.properties")) {
            props = new Properties();
            props.load(input);
            return true;
        } catch (FileNotFoundException ex) {
            return initializeConfig();
        } catch (IOException ex) {
            return false;
        }
    }

    public static Integer getInt(String prop) {
        return Integer.parseInt(props.getProperty(prop));
    }

    public static String getString(String prop) {
        return props.getProperty(prop, "");
    }

    private static Boolean initializeConfig() {
        props = new Properties();
        props.setProperty("lobby.port", "6778");
        props.setProperty("sfs2x.ip", "127.0.0.1");
        props.setProperty("sfs2x.port", "9933");
        props.setProperty("sockpol.port", "843");
        try (OutputStream out = new FileOutputStream("config.properties")) {
            props.store(out, null);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
