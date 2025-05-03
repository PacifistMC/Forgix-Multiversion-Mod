package io.github.pacifistmc.forgix.multiversion;

import com.google.common.collect.ImmutableMap;
import com.mojang.logging.LogUtils;
import cpw.mods.jarhandling.SecureJar;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.moddiscovery.AbstractModProvider;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.ModFileParser;
import net.minecraftforge.forgespi.locating.IDependencyLocator;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.ModFileLoadingException;
import org.slf4j.Logger;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
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
                        String path = Forgix.getPathForVersion(minecraftVersion, inputStream);
                        if (path == null) return List.<IModFile>of();
                        return loadModFileFrom(modFile, path)
                                .map(List::of)
                                .orElse(List.of());
                    })
                    .orElse(List.of());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    // Code copied from forg themselves 😎 (copied as I don't want to rely on forge too much)
    // It's from JarInJarDependencyLocator

    protected Optional<IModFile> loadModFileFrom(IModFile file, String path) {
        try {
            Path pathInModFile = file.findResource(path);
            URI filePathUri = new URI("jij:" + (pathInModFile.toAbsolutePath().toUri().getRawSchemeSpecificPart())).normalize();
            try (FileSystem zipFS = FileSystems.newFileSystem(filePathUri, ImmutableMap.of("packagePath", pathInModFile))) {
                IModFile.Type parentType = file.getType();
                String modType = switch (parentType) {
                    case LIBRARY, LANGPROVIDER -> IModFile.Type.LIBRARY.name();
                    default -> IModFile.Type.GAMELIBRARY.name();
                };
                return Optional.of(createMod(zipFS.getPath("/"), false, modType).file());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load mod file {} from {}", path, file.getFileName());
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
