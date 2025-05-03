package io.github.pacifistmc.forgix.multiversion;

import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileLocator;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.ModFileParser;
import net.minecraftforge.forgespi.locating.IModFile;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;

// I hate the code in this file, please someone figure out a way to load nested jar mods for 1.16.5 and below
// Or a good way of multiversion mods
public class ForgeMultiversionLocator extends AbstractJarFileLocator {
    private static final String minecraftVersion;
    private static final Path tempDir = FMLPaths.MODSDIR.get().resolve(".forgix-multiversion-jars");

    static {
        try {
            Field mcVersionField = Class.forName("net.minecraftforge.fml.loading.FMLLoader").getDeclaredField("mcVersion");
            mcVersionField.setAccessible(true);
            minecraftVersion = (String) mcVersionField.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (Files.exists(tempDir)) deleteDirectory(tempDir);
        createTemporaryDirectory(tempDir);
    }

    @Override
    public List<IModFile> scanMods() {
        try (var inputStream = ForgeMultiversionLocator.class.getResourceAsStream("/" + Forgix.MULTI_VERSION_LOCATION)) {
            String path = Forgix.getPathForVersion(minecraftVersion, inputStream);
            if (path == null) return List.of();

            // Create a valid filename from the path
            Path outputJarPath = tempDir.resolve(UUID.randomUUID() + "-" + new File(path).getName());

            extractNestedJar(path, outputJarPath);
            var modFile = new ModFile(outputJarPath, this, ModFileParser::modsTomlParser);
            modJars.put(modFile, createFileSystem(modFile));
            return List.of(modFile);
        } catch (IOException e) {
            throw new RuntimeException("Failed to scan mods", e);
        }
    }

    public void extractNestedJar(String pathInJar, Path outputJarPath) {
        try {
            if (outputJarPath == null) throw new IllegalArgumentException("Output path cannot be null");
            
            // Ensure parent directory exists
            Path parent = outputJarPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            
            try (var inputStream = ForgeMultiversionLocator.class.getResourceAsStream("/" + pathInJar)) {
                if (inputStream == null) throw new RuntimeException("Could not find " + pathInJar + " in jar");
                Files.copy(inputStream, outputJarPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract jar: " + pathInJar + " to " + outputJarPath, e);
        }
    }

    private static void createTemporaryDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> { // File.deleteOnExit() is not reliable, so we use a shutdown hook instead
                deleteDirectory(directory);
            }));
            Files.setAttribute(directory, "dos:hidden", true);
        } catch (IOException ignored) { }
    }

    private static void deleteDirectory(Path directory) {
        try (var walk = Files.walk(directory)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException ignored) { }
                    });
        } catch (IOException ignored) { }
    }

    @Override
    public String name() {
        return "forgix-multiversion";
    }

    @Override
    public void initArguments(Map<String, ?> arguments) { }
}
