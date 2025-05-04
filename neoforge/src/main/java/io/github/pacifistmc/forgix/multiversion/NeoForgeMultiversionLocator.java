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
import java.util.List;
import java.util.Optional;

public class NeoForgeMultiversionLocator implements IDependencyLocator {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String minecraftVersion = FMLLoader.versionInfo().mcVersion();

    @Override
    public void scanMods(List<IModFile> loadedMods, IDiscoveryPipeline pipeline) {
        try {
            SecureJar secureJar = SecureJar.from(Path.of(NeoForgeMultiversionLocator.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
            IModFile modFile = IModFile.create(secureJar, JarModsDotTomlModFileReader::manifestParser);
            loadResourceFromModFile(modFile, Forgix.MULTI_VERSION_LOCATION).ifPresent(inputStream -> {
                String path = Forgix.getPathForVersion(minecraftVersion, inputStream);
                if (path == null) return;
                loadModFileFrom(modFile, path, pipeline).ifPresent(pipeline::addModFile);
            });
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    // Code copied from forg themselves 😎 (copied as I don't want to rely on forge too much)
    // It's from JarInJarDependencyLocator

    @SuppressWarnings("resource") // Don't close the file system as it's used somewhere else internally by forg
    protected Optional<IModFile> loadModFileFrom(IModFile file, String path, IDiscoveryPipeline pipeline) {
        try {
            var pathInModFile = file.findResource(path);
            var filePathUri = new URI("jij:" + (pathInModFile.toAbsolutePath().toUri().getRawSchemeSpecificPart())).normalize();
            var outerFsArgs = ImmutableMap.of("packagePath", pathInModFile);
            var zipFS = FileSystems.newFileSystem(filePathUri, outerFsArgs);
            var jar = JarContents.of(zipFS.getPath("/"));
            var providerResult = pipeline.readModFile(jar, ModFileDiscoveryAttributes.DEFAULT.withParent(file));
            return Optional.ofNullable(providerResult);
        } catch (Exception e) {
            LOGGER.error("Failed to load mod file {} from {}", path, file.getFileName());
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
