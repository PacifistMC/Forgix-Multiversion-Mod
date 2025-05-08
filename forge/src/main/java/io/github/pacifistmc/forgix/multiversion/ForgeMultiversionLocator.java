package io.github.pacifistmc.forgix.multiversion;

import com.google.common.collect.ImmutableMap;
import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.SecureJar;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.moddiscovery.*;
import net.minecraftforge.forgespi.locating.IDependencyLocator;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.ModFileLoadingException;
import org.slf4j.Logger;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ForgeMultiversionLocator extends AbstractModProvider implements IDependencyLocator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String minecraftVersion = FMLLoader.versionInfo().mcVersion();

    @Override
    public List<IModFile> scanMods(Iterable<IModFile> loadedMods) {
        try {
            SecureJar secureJar = SecureJar.from(Path.of(ForgeMultiversionLocator.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
            IModFile modFile = new ModFile(secureJar, this, ModFileParser::modsTomlParser);
            return loadResourceFromModFile(modFile, Forgix.MULTI_VERSION_LOCATION)
                    .map(inputStream -> {
                        var forgix = Forgix.getForgixVersionJson(inputStream);
                        if (forgix == null) return List.<IModFile>of();
                        var versionPath = forgix.getPathForVersion(minecraftVersion);
                        if (versionPath == null) return List.<IModFile>of();
                        return loadModFileFrom(modFile, versionPath, forgix.getSharedLibrary())
                                .map(List::of)
                                .orElse(List.of());
                    })
                    .orElse(List.of());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    // Code copied from forg themselves 😎 (copied as I don't want to rely on forge too much)
    // It's from JarInJarDependencyLocator but modified to fit our needs

    @SuppressWarnings("resource") // Don't close the file system as it's used somewhere else internally by forg
    protected Optional<IModFile> loadModFileFrom(IModFile file, String versionPath, String sharedLibraryPath) {
        try {
            List<Path> jarPaths = new ArrayList<>();

            var versionPathInModFile = file.findResource(versionPath);
            var versionZipFS = FileSystems.newFileSystem(
                    new URI("jij:" + (versionPathInModFile.toAbsolutePath().toUri().getRawSchemeSpecificPart())).normalize(),
                    ImmutableMap.of("packagePath", versionPathInModFile)
            );
            jarPaths.add(versionZipFS.getPath("/"));

            if (sharedLibraryPath != null) {
                var sharedLibraryPathInModFile = file.findResource(sharedLibraryPath);
                var sharedLibraryZipFS = FileSystems.newFileSystem(
                        new URI("jij:" + (sharedLibraryPathInModFile.toAbsolutePath().toUri().getRawSchemeSpecificPart())).normalize(),
                        ImmutableMap.of("packagePath", sharedLibraryPathInModFile)
                );
                jarPaths.add(sharedLibraryZipFS.getPath("/"));
            }
            return Optional.of(new ModFile(SecureJar.from(jarPaths.toArray(Path[]::new)), this, ModFileParser::modsTomlParser));
        } catch (Exception e) {
            LOGGER.error("Failed to load mod file {} from {}", versionPath, file.getFileName());
            var exception = new ModFileLoadingException("Failed to load mod file " + file.getFileName());
            exception.initCause(e);
            throw exception;
        }
    }

    protected Optional<InputStream> loadResourceFromModFile(IModFile modFile, String path) {
        try {
            return Optional.of(Files.newInputStream(modFile.findResource(path)));
        } catch (NoSuchFileException e) {
            LOGGER.trace("Failed to load resource {} from {}, it does not contain dependency information.", path, modFile.getFileName());
        } catch (Exception e) {
            LOGGER.error("Failed to load resource {} from mod {}, cause {}", path, modFile.getFileName(), e);
        }
        return Optional.empty();
    }

    @Override
    public String name() {
        return "forgix-multiversion";
    }
}
