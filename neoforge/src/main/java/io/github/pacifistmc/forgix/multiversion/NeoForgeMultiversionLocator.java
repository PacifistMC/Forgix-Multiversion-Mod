package io.github.pacifistmc.forgix.multiversion;

import com.google.common.collect.ImmutableMap;
import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.SecureJar;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.neoforgespi.locating.*;
import org.slf4j.Logger;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.*;

public class NeoForgeMultiversionLocator implements IDependencyLocator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String minecraftVersion = FMLLoader.versionInfo().mcVersion();

    @Override
    public void scanMods(List<IModFile> loadedMods, IDiscoveryPipeline pipeline) {
        try {
            SecureJar secureJar = SecureJar.from(Path.of(NeoForgeMultiversionLocator.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
            IModFile modFile = IModFile.create(secureJar, JarModsDotTomlModFileReader::manifestParser);
            loadResourceFromModFile(modFile, Forgix.MULTI_VERSION_LOCATION).ifPresent(inputStream -> {
                var forgix = Forgix.getForgixVersionJson(inputStream);
                if (forgix == null) return;
                var versionPath = forgix.getPathForVersion(minecraftVersion);
                if (versionPath == null) return;
                loadModFileFrom(modFile, versionPath, forgix.getSharedLibrary(), pipeline).ifPresent(pipeline::addModFile);
            });
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    // Code copied from forg themselves 😎
    // It's from JarInJarDependencyLocator but modified to fit our needs

    @SuppressWarnings("resource") // Don't close the file system as it's used somewhere else internally by forg
    protected Optional<IModFile> loadModFileFrom(IModFile file, String versionPath, String sharedLibraryPath, IDiscoveryPipeline pipeline) {
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

            var jar = JarContents.of(jarPaths);
            var providerResult = pipeline.readModFile(jar, ModFileDiscoveryAttributes.DEFAULT.withParent(file));
            return Optional.ofNullable(providerResult);
        } catch (Exception e) {
            LOGGER.error("Failed to load mod file {} from {}", versionPath, file.getFileName());
            RuntimeException exception = new ModFileLoadingException("Failed to load mod file " + file.getFileName());
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
}
