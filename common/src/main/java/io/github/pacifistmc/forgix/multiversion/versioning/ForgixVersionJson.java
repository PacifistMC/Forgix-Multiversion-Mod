package io.github.pacifistmc.forgix.multiversion.versioning;

// Json file containing map of versions of the mod and String path inside the jar
//```json
//{
//  "versions": {
//    "[1.16.2,)": "META-INF/forgix/multiversion/example.jar",
//    "[1.16.5,)": "META-INF/forgix/multiversion/example2.jar"
//  }
//  "sharedLibrary": "META-INF/forgix/multiversion/shared.multiversion.jar"
//}
//```

import org.apache.maven.artifact.versioning.ArtifactVersion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ForgixVersionJson {
    private final Map<String, String> versions;
    private String sharedLibrary;

    public ForgixVersionJson() {
        this.versions = new HashMap<>();
        this.sharedLibrary = null;
    }

    public Map<String, String> getVersions() {
        return versions;
    }

    public String getSharedLibrary() {
        return sharedLibrary;
    }

    public void setSharedLibrary(String sharedLibrary) {
        this.sharedLibrary = sharedLibrary;
    }

    /**
     * Gets the path for the highest matching version range for the given version.
     *
     * @param version The version to check
     * @return The path for the highest matching version range, or null if no match is found
     */
    public String getPathForVersion(String version) {
        return versions.entrySet().stream()
                .filter(entry -> VersionRangeParser.containsVersion(entry.getKey(), version))
                .max((a, b) -> {
                    ArtifactVersion versionA = VersionRangeParser.getLowerBound(a.getKey());
                    ArtifactVersion versionB = VersionRangeParser.getLowerBound(b.getKey());
                    if (versionA == null) return -1;
                    if (versionB == null) return 1;
                    return versionA.compareTo(versionB);
                })
                .map(Map.Entry::getValue)
                .orElse(null);
    }
}
