package io.github.pacifistmc.forgix.multiversion;

import com.google.gson.Gson;
import io.github.pacifistmc.forgix.multiversion.versioning.ForgixVersionJson;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.util.Optional;
import java.util.function.Predicate;

public class Forgix {
    public static final String MULTI_VERSION_LOCATION = "META-INF/forgix/multiversion.json";

    /**
     * Gets the ForgixVersionJson from the input stream.
     *
     * @param inputStream The input stream
     * @return The ForgixVersionJson, or null if it could not be parsed
     */
    public static ForgixVersionJson getForgixVersionJson(InputStream inputStream) {
        if (inputStream == null) return null;
        try {
            return new Gson().fromJson(
                    new String(inputStream.readAllBytes()),
                    ForgixVersionJson.class
            );
        } catch (IOException e) {
            return null;
        }
    }

    public static void extractNestedJar(String versionPathInJar, String sharedLibraryPathInJar, Path outputJarPath) {
        if (outputJarPath == null) throw new IllegalArgumentException("Output path cannot be null");

        // Ensure parent directory exists
        Optional.ofNullable(outputJarPath.getParent()).ifPresent(parent -> {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create parent directories", e);
            }
        });
        
        try (var zos = new ZipOutputStream(Files.newOutputStream(outputJarPath))) {
            // Process version-specific jar
            processNestedJar(versionPathInJar, zos, name -> true);
            
            // Process shared library jar if it exists
            if (sharedLibraryPathInJar != null) {
                processNestedJar(sharedLibraryPathInJar, zos, name -> !name.equals("fabric.mod.json"));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract jar: " + versionPathInJar + " to " + outputJarPath, e);
        }
    }

    private static void processNestedJar(String jarPath, ZipOutputStream zos, Predicate<String> entryFilter) throws IOException {
        try (var jarStream = Forgix.class.getResourceAsStream("/" + jarPath)) {
            if (jarStream == null) throw new RuntimeException("Could not find " + jarPath + " in jar");
            
            var zis = new ZipInputStream(jarStream);
            ZipEntry entry;
            
            while ((entry = zis.getNextEntry()) != null) {
                // Skip directories
                if (entry.isDirectory()) {
                    zis.closeEntry();
                    continue;
                }
                
                var name = entry.getName();
                // Skip entries that don't pass the filter
                if (!entryFilter.test(name)) {
                    zis.closeEntry();
                    continue;
                }
                
                zos.putNextEntry(new ZipEntry(name));
                zis.transferTo(zos);
                zos.closeEntry();
                zis.closeEntry();
            }
        }
    }
}
