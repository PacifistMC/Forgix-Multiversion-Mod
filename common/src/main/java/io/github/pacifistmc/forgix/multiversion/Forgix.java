package io.github.pacifistmc.forgix.multiversion;

import com.google.gson.Gson;
import io.github.pacifistmc.forgix.multiversion.versioning.ForgixVersionJson;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.net.URL;

public class Forgix {
    public static final String MULTI_VERSION_LOCATION = "META-INF/forgix/multiversion.json";
    private static final Set<String> EXCLUDE_FILES = Set.of("META-INF/", "fabric.mod.json", "pack.mcmeta"); // Files to exclude when copying from the current jar

    /**
     * Gets the path for a specific version based on the version ranges in the map.
     *
     * @param version The version to find a path for
     * @param inputStream The input stream containing the version mappings
     * @return The path for the best matching version range, or null if no match is found
     */
    public static String getPathForVersion(String version, InputStream inputStream) {
        if (inputStream == null) return null;
        try {
            return new Gson().fromJson(
                    new String(inputStream.readAllBytes()),
                    ForgixVersionJson.class
            ).getPathForVersion(version);
        } catch (IOException e) {
            return null;
        }
    }


     /**
     * Gets the path of the current jar file.
     * @return The path of the current jar file
     */
    public static String getCurrentJarLocation() {
        return Forgix.class.getProtectionDomain().getCodeSource().getLocation().getPath();
    }

    public static void extractNestedJar(String pathInJar, Path outputJarPath) {
        try {
            if (outputJarPath == null) throw new IllegalArgumentException("Output path cannot be null");

            // Ensure parent directory exists
            Path parent = outputJarPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            
            // Track entries we've already added (from nested jar)
            Set<String> addedEntries = new HashSet<>();
            
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(outputJarPath))) {
                // First, copy contents from the nested jar
                try (InputStream nestedJarStream = Forgix.class.getResourceAsStream("/" + pathInJar)) {
                    if (nestedJarStream == null) throw new RuntimeException("Could not find " + pathInJar + " in jar");

                    ZipInputStream nestedZis = new ZipInputStream(nestedJarStream);
                    ZipEntry entry;
                    byte[] buffer = new byte[8192];

                    while ((entry = nestedZis.getNextEntry()) != null) {
                        if (!entry.isDirectory()) {
                            String name = entry.getName();
                            addedEntries.add(name);

                            ZipEntry newEntry = new ZipEntry(name);
                            zos.putNextEntry(newEntry);

                            int len;
                            while ((len = nestedZis.read(buffer)) > 0) {
                                zos.write(buffer, 0, len);
                            }

                            zos.closeEntry();
                        }
                        nestedZis.closeEntry();
                    }
                }

                // Then copy from the current jar, excluding specified files and already added entries
                URL jarUrl = Forgix.class.getProtectionDomain().getCodeSource().getLocation();
                try (ZipInputStream currentZis = new ZipInputStream(jarUrl.openStream())) {
                    ZipEntry entry;
                    byte[] buffer = new byte[8192];

                    while ((entry = currentZis.getNextEntry()) != null) {
                        if (!entry.isDirectory()) {
                            String name = entry.getName();

                            // Skip if already added or should be excluded
                            if (addedEntries.contains(name) || shouldExclude(name) || name.startsWith("forgix_multiversion")) continue;
                            ZipEntry newEntry = new ZipEntry(name);
                            zos.putNextEntry(newEntry);

                            int len;
                            while ((len = currentZis.read(buffer)) > 0) {
                                zos.write(buffer, 0, len);
                            }

                            zos.closeEntry();
                        }
                        currentZis.closeEntry();
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract jar: " + pathInJar + " to " + outputJarPath, e);
        }
    }
    
    private static boolean shouldExclude(String path) {
        return Forgix.EXCLUDE_FILES.stream().anyMatch(pattern ->
            pattern.endsWith("/") 
                ? path.startsWith(pattern) || path.equals(pattern.substring(0, pattern.length() - 1))
                : path.equals(pattern));
    }
}
