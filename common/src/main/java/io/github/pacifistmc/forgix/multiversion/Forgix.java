package io.github.pacifistmc.forgix.multiversion;

import com.google.gson.Gson;
import io.github.pacifistmc.forgix.multiversion.versioning.ForgixVersionJson;

import java.io.IOException;
import java.io.InputStream;

public class Forgix {
    public static final String MULTI_VERSION_LOCATION = "META-INF/forgix/multiversion.json";

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
}
