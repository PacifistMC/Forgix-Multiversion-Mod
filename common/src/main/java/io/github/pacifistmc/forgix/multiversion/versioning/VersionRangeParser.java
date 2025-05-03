package io.github.pacifistmc.forgix.multiversion.versioning;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.Restriction;
import org.apache.maven.artifact.versioning.VersionRange;

/**
 * Utility class for parsing and handling Maven version ranges.
 * Supports all standard Maven version range formats:
 *
 * 1.0                 x >= 1.0 (redefined as minimum version)
 * (,1.0]              x <= 1.0
 * (,1.0)              x < 1.0
 * [1.0]               x == 1.0
 * [1.0,)              x >= 1.0
 * (1.0,)              x > 1.0
 * (1.0,2.0)           1.0 < x < 2.0
 * [1.0,2.0]           1.0 <= x <= 2.0
 * (,1.0],[1.2,)       x <= 1.0 or x >= 1.2
 * (,1.1),(1.1,)       x != 1.1
 */
public class VersionRangeParser {
    /**
     * Parses a version range string and checks if the given version is contained within it.
     *
     * @param rangeStr The version range string to parse
     * @param version  The version to check
     * @return true if the version is contained in the range, false otherwise
     */
    public static boolean containsVersion(String rangeStr, String version) {
        try {
            DefaultArtifactVersion artifactVersion = new DefaultArtifactVersion(version);

            // Handle the special case where the range is just a version number (e.g. "1.0")
            if (!rangeStr.contains(",") && !rangeStr.contains("(") && !rangeStr.contains("[") &&
                    !rangeStr.contains(")") && !rangeStr.contains("]")) {
                rangeStr = "[" + rangeStr + "]";
            }

            // For standard version ranges, use Maven's VersionRange
            VersionRange range = VersionRange.createFromVersionSpec(rangeStr);
            return range.containsVersion(artifactVersion);
        } catch (Exception e) {
            // Log the error or handle it as needed
            System.err.println("Error parsing version range '" + rangeStr + "': " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets the minimum version from a version range.
     *
     * @param range The version range
     * @return The minimum version in the range, or null if there is no lower bound
     */
    public static ArtifactVersion getMinVersion(VersionRange range) {
        if (range.getRestrictions().isEmpty()) {
            return null;
        }

        ArtifactVersion minVersion = null;
        for (Restriction restriction : range.getRestrictions()) {
            ArtifactVersion lowerBound = restriction.getLowerBound();
            if (lowerBound != null) {
                if (minVersion == null || lowerBound.compareTo(minVersion) < 0) {
                    minVersion = lowerBound;
                }
            }
        }

        return minVersion;
    }

    /**
     * Gets the lower bound version from a version range string.
     *
     * @param rangeStr The version range string to parse
     * @return The lower bound version, or null if there is no lower bound
     */
    public static ArtifactVersion getLowerBound(String rangeStr) {
        try {
            // Handle the special case where the range is just a version number
            if (!rangeStr.contains(",") && !rangeStr.contains("(") && !rangeStr.contains("[") &&
                    !rangeStr.contains(")") && !rangeStr.contains("]")) {
                return new DefaultArtifactVersion(rangeStr);
            }

            VersionRange range = VersionRange.createFromVersionSpec(rangeStr);
            return getMinVersion(range);
        } catch (Exception e) {
            System.err.println("Error parsing version range '" + rangeStr + "': " + e.getMessage());
            return null;
        }
    }
}
