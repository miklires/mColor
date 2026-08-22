package io.github.miklires.mcolor.update;

import io.github.miklires.mcolor.MColorPlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateChecker {
    private static final Pattern VERSION = Pattern.compile("\\\"version_number\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private final MColorPlugin plugin;

    public UpdateChecker(MColorPlugin plugin) { this.plugin = plugin; }

    public void start() {
        if (!plugin.getConfig().getBoolean("updates.enabled", true)) return;
        String project = plugin.getConfig().getString("updates.project", "").trim();
        if (project.isEmpty()) return;
        plugin.scheduler().async(() -> check(project));
    }

    private void check(String project) {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()) {
            String filters = "?loaders=%5B%22paper%22%5D&game_versions=%5B%2226.2%22%5D";
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.modrinth.com/v2/project/" + project + "/version" + filters))
                    .timeout(Duration.ofSeconds(5)).header("User-Agent", "miklires/mColor").build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return;
            Matcher matcher = VERSION.matcher(response.body());
            if (!matcher.find()) return;
            String latest = matcher.group(1);
            if (new Version(latest).newerThan(new Version(plugin.getPluginMeta().getVersion()))) {
                plugin.getLogger().info("mColor " + latest + " is available on Modrinth");
            }
        } catch (Exception exception) {
            plugin.getLogger().fine("Update check failed: " + exception.getMessage());
        }
    }

    private record Version(int major, int minor, int patch) implements Comparable<Version> {
        Version(String value) { this(numbers(value, 0), numbers(value, 1), numbers(value, 2)); }
        private static int numbers(String value, int index) {
            String[] parts = value.split("[-+]")[0].split("\\.");
            if (index >= parts.length) return 0;
            try { return Integer.parseInt(parts[index].replaceAll("[^0-9]", "")); }
            catch (NumberFormatException ignored) { return 0; }
        }
        boolean newerThan(Version current) { return compareTo(current) > 0; }
        @Override public int compareTo(Version other) {
            int majorComparison = Integer.compare(major, other.major);
            if (majorComparison != 0) return majorComparison;
            int minorComparison = Integer.compare(minor, other.minor);
            return minorComparison != 0 ? minorComparison : Integer.compare(patch, other.patch);
        }
    }
}
