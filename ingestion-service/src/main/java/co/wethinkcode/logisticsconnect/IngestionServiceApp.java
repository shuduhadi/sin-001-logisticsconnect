package co.wethinkcode.logisticsconnect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import io.javalin.Javalin;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stage 1 of the LogisticsConnect pipeline.
 *
 * <p>Reads the messy legacy export {@code hubs-global.csv} from the classpath,
 * normalizes every field (casing, padding, placeholder values, boolean/flag
 * variants, province spelling variants), resolves duplicate rows that describe
 * the same real-world hub under different IDs, and serves the cleaned result
 * over REST at {@code GET /hubs} for the other LogisticsConnect services to
 * consume.
 *
 * <p>See {@code ingestion-service/README.md} for the full list of known data
 * issues this class is responsible for handling.
 */
public class IngestionServiceApp {

    /** Raw string values that mean "no data was provided", across any column. */
    private static final Set<String> PLACEHOLDER_VALUES = Set.of(
            "", "n/a", "na", "tbd", "unknown", "-", "nan"
    );

    /** Raw string values (lowercased before comparison) that mean {@code true} for the active flag. */
    private static final Set<String> TRUE_VALUES = Set.of("y", "yes", "true", "1");

    /** Raw string values (lowercased before comparison) that mean {@code false} for the active flag. */
    private static final Set<String> FALSE_VALUES = Set.of("n", "no", "false", "0");

    /**
     * Maps every spelling/formatting variant of a province name seen in the source
     * data onto one canonical, title-cased form. Lookup key is always lowercased
     * first. A province not present here falls back to generic title-casing
     * rather than being rejected, so unseen-but-valid provinces still pass through.
     */
    private static final Map<String, String> PROVINCE_ALIASES = Map.ofEntries(
            Map.entry("gauteng", "Gauteng"),
            Map.entry("western cape", "Western Cape"),
            Map.entry("eastern cape", "Eastern Cape"),
            Map.entry("kwazulu-natal", "KwaZulu-Natal"),
            Map.entry("kwazulu natal", "KwaZulu-Natal"),
            Map.entry("kwa-zulu natal", "KwaZulu-Natal"),
            Map.entry("kwa zulu natal", "KwaZulu-Natal"),
            Map.entry("free state", "Free State"),
            Map.entry("limpopo", "Limpopo"),
            Map.entry("north west", "North West"),
            Map.entry("mpumalanga", "Mpumalanga"),
            Map.entry("northern cape", "Northern Cape")
    );

    /** Matches the trailing digits of a hub ID (e.g. "H-501" -> "501"), used to sort/rank duplicate IDs numerically. */
    private static final Pattern TRAILING_DIGITS = Pattern.compile("(\\d+)\\s*$");

      /** Matches an ISO date: {@code YYYY-M-D}, 1- or 2-digit month/day. Capture groups: (year, month, day). */
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})");

    /** Matches a slash US-style date: {@code M/D/YYYY}, 1- or 2-digit month/day. Capture groups: (month, day, year). */
    private static final Pattern SLASH_MDY_PATTERN = Pattern.compile("(\\d{1,2})/(\\d{1,2})/(\\d{4})");

    /** Matches a dash day-first date: {@code D-M-YYYY}, 1- or 2-digit day/month. Capture groups: (day, month, year). */
    private static final Pattern DASH_DMY_PATTERN = Pattern.compile("(\\d{1,2})-(\\d{1,2})-(\\d{4})");

    /**
     * Loads and cleans {@code hubs-global.csv} once at startup, then starts the
     * Javalin server on port 7050 and serves the cleaned, deduplicated records
     * at {@code GET /hubs}.
     *
     * @throws IOException if {@code hubs-global.csv} cannot be found or parsed
     */
    public static void main(String[] args) throws IOException {
        List<HubRecord> cleanedHubs = loadAndCleanHubs();

        Javalin app = Javalin.create().start(7050);
        ObjectMapper mapper = new ObjectMapper();

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/hubs", ctx -> ctx.json(cleanedHubs));

        System.out.printf("Loaded %d cleaned hub record(s) from hubs-global.csv%n", cleanedHubs.size());
    }

    /**
     * Load + clean:

     * Reads {@code /hubs-global.csv} from the classpath, normalizes every field
     * of every row, drops rows that are unsalvageable (missing hub ID or
     * sorting center after cleaning), and resolves duplicate rows via
     * {@link #dedupe}.
     *
     * @return the cleaned, deduplicated list of hub records
     * @throws IOException if the CSV resource is missing or malformed
     */
    static List<HubRecord> loadAndCleanHubs() throws IOException {
        List<String[]> rawRows = readCsv("/hubs-global.csv");
        List<HubRecord> parsed = new ArrayList<>();

        // rawRows.get(0) is the header row - skip it.
        for (int i = 1; i < rawRows.size(); i++) {
            String[] row = rawRows.get(i);
            if (row.length < 4) {
                System.err.printf("Skipping malformed row %d: %s%n", i, Arrays.toString(row));
                continue;
            }

            String hubId = normalizeHubId(row[0]);
            String province = normalizeProvince(row[1]);
            String sortingCenter = normalizeText(row[2]);
            Boolean active = parseBoolean(row[3]);

            if (hubId.isEmpty() || sortingCenter.isEmpty()) {
                System.err.printf("Skipping row %d - missing hub_id or sorting_center%n", i);
                continue;
            }

            parsed.add(new HubRecord(hubId, province, sortingCenter, active, new ArrayList<>()));
        }

        return dedupe(parsed);
    }

    /**
     * Reads every row of a CSV file on the classpath, including the header row.
     *
     * @param classpathResource resource path, e.g. {@code "/hubs-global.csv"}
     * @return all rows, each as an array of raw (unnormalized) field values
     * @throws IOException if the resource is missing or cannot be parsed as CSV
     */
    private static List<String[]> readCsv(String classpathResource) throws IOException {
        try (InputStream in = IngestionServiceApp.class.getResourceAsStream(classpathResource)) {
            if (in == null) {
                throw new IOException("Could not find resource on classpath: " + classpathResource);
            }
            try (CSVReader reader = new CSVReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return reader.readAll();
            } catch (com.opencsv.exceptions.CsvException e) {
                throw new IOException("Failed to parse hubs-global.csv", e);
            }
        }
    }

    
    /**
     * Deduplication:
     * 
     * Groups records that describe the same real-world hub and collapses each
     * group into one canonical {@link HubRecord}.
     *
     * <p>Records are grouped by normalized {@code sorting_center} rather than
     * {@code hub_id} or {@code province}, because hub IDs appear under
     * different casing/format for the same hub, and province is sometimes
     * blank for one member of a group (see {@code H-508} in the source data).
     * Sorting center is the one field reliably shared by true duplicates.
     *
     * <p>Within each group:
     * <ul>
     *   <li>the canonical {@code hub_id} is the one with the lowest trailing
     *       number (see {@link #hubIdSortKey})</li>
     *   <li>{@code province} is backfilled from any non-null member if the
     *       canonical record's own province is null</li>
     *   <li>{@code active} is resolved by {@link #resolveActive}</li>
     *   <li>every other ID in the group is recorded in the result's
     *       {@code mergedFrom} list, for auditability</li>
     * </ul>
     *
     * @param records cleaned (but not yet deduplicated) records
     * @return one record per distinct real-world hub
     */
    static List<HubRecord> dedupe(List<HubRecord> records) {
        Map<String, List<HubRecord>> groups = new LinkedHashMap<>();
        for (HubRecord r : records) {
            groups.computeIfAbsent(r.getSortingCenter().toLowerCase(), k -> new ArrayList<>()).add(r);
        }

        List<HubRecord> result = new ArrayList<>();
        for (List<HubRecord> group : groups.values()) {
            group.sort(Comparator.comparingInt(IngestionServiceApp::hubIdSortKey));

            HubRecord canonical = group.get(0);
            String resolvedProvince = canonical.getProvince();
            if (resolvedProvince == null) {
                resolvedProvince = group.stream()
                        .map(HubRecord::getProvince)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);
            }

            List<String> mergedFrom = new ArrayList<>();
            for (int i = 1; i < group.size(); i++) {
                mergedFrom.add(group.get(i).getHubId());
            }

            result.add(new HubRecord(
                    canonical.getHubId(),
                    resolvedProvince,
                    canonical.getSortingCenter(),
                    resolveActive(group),
                    mergedFrom
            ));
        }
        return result;
    }

    /**
     * Resolves the {@code active} flag for a group of duplicate records by
     * majority vote among non-placeholder values. Ties are resolved to
     * {@code true}: wrongly marking a live hub inactive silently drops real
     * capacity from downstream ETA calculations, whereas the reverse only
     * costs an extra availability check.
     *
     * @param group duplicate records for one real-world hub
     * @return {@code true}/{@code false} if a majority exists, or {@code null}
     *         if every member's value was a placeholder (unknown either way)
     */
    private static Boolean resolveActive(List<HubRecord> group) {
        long trueCount = group.stream().filter(r -> Boolean.TRUE.equals(r.getActive())).count();
        long falseCount = group.stream().filter(r -> Boolean.FALSE.equals(r.getActive())).count();

        if (trueCount == 0 && falseCount == 0) return null; // every value in the group was a placeholder
        return trueCount >= falseCount;
    }

    /**
     * Sort key used to pick the canonical ID within a duplicate group: the
     * numerically lowest trailing number wins (e.g. "H-500" before "H-510").
     * IDs with no trailing digits sort last rather than throwing.
     */
    private static int hubIdSortKey(HubRecord r) {
        Matcher m = TRAILING_DIGITS.matcher(r.getHubId());
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return Integer.MAX_VALUE; // ids with no trailing number sort last
    }
    
    /**
     * Field normalization (package-visible / static so they're unit-testable):

     * Normalizes a hub ID: trims padding, collapses internal double spaces,
     * and uppercases (so {@code " h-501 "} and {@code "H-501"} become the
     * same value).
     *
     * @param raw raw hub_id field value, may be {@code null}
     * @return the normalized ID, or {@code ""} if {@code raw} was {@code null}
     */
    public static String normalizeHubId(String raw) {
        if (raw == null) return "";
        return collapseWhitespace(raw).toUpperCase();
    }

    /**
     * Normalizes a province name: trims/collapses whitespace, maps known
     * spelling/formatting variants to one canonical form via
     * {@link #PROVINCE_ALIASES}, and falls back to generic title-casing for
     * anything not in that map.
     *
     * @param raw raw province field value, may be {@code null}
     * @return the canonical province name, or {@code null} if the value was
     *         blank or a recognized placeholder (e.g. {@code "N/A"})
     */
    static String normalizeProvince(String raw) {
        String cleaned = collapseWhitespace(raw);
        if (isPlaceholder(cleaned)) return null;
        String alias = PROVINCE_ALIASES.get(cleaned.toLowerCase());
        return alias != null ? alias : toTitleCase(cleaned);
    }

    /**
     * Normalizes a free-text field such as {@code sorting_center}: trims and
     * collapses whitespace, then title-cases it.
     *
     * @param raw raw field value, may be {@code null}
     * @return the normalized text, or {@code ""} if the value was blank or a
     *         recognized placeholder
     */
    static String normalizeText(String raw) {
        String cleaned = collapseWhitespace(raw);
        if (isPlaceholder(cleaned)) return "";
        return toTitleCase(cleaned);
    }

    /**
     * Normalizes a boolean/flag field. Recognizes, case-insensitively:
     * {@code Y/yes/true/1} as {@code true} and {@code N/no/false/0} as
     * {@code false}. A missing or unrecognized value returns {@code null}
     * rather than defaulting to {@code false}, since "unknown" and "inactive"
     * are meaningfully different states.
     *
     * @param raw raw field value, may be {@code null}
     * @return {@code true}, {@code false}, or {@code null} if unrecognized
     */
    static Boolean parseBoolean(String raw) {
        String cleaned = collapseWhitespace(raw).toLowerCase();
        if (TRUE_VALUES.contains(cleaned)) return true;
        if (FALSE_VALUES.contains(cleaned)) return false;
        return null; // placeholder or unrecognized value - treat as unknown, not false
    }

  
    /**
     * Normalizes a date string in ISO ({@code YYYY-MM-DD}), slash
     * ({@code MM/DD/YYYY}), or dash ({@code DD-MM-YYYY}) format — each
     * accepting 1- or 2-digit month/day — into a {@link LocalDate}.
     *
     * <p>The separator disambiguates format per the README's spec: a
     * {@code "-"}-separated date with a 4-digit leading group is treated as
     * ISO ({@code YYYY-MM-DD}); a {@code "-"}-separated date with a 1- or
     * 2-digit leading group is treated as day-first ({@code DD-MM-YYYY}); a
     * {@code "/"}-separated date is always month-first
     * ({@code MM/DD/YYYY}).
     *
     * <p>Digits are extracted with a regex and built into a date directly
     * via {@link LocalDate#of(int, int, int)}, which throws on a
     * calendar-invalid date (e.g. {@code "2023-02-30"}), rather than going
     * through {@link java.time.format.DateTimeFormatter} — this avoids any
     * ambiguity in how a formatter resolves variable-width numeric fields.
     *
     * <p>Returns {@code null} rather than throwing for: placeholder values,
     * strings that don't match any of the three known shapes, and strings
     * that match a shape but describe a calendar-invalid date — a single
     * bad date shouldn't fail the whole ingestion run.
     *
     * <p>Not currently called anywhere in this pipeline, since
     * {@code hubs-global.csv} has no date column — kept available for reuse
     * if one is added.
     *
     * @param raw raw date field value, may be {@code null}
     * @return the parsed date, or {@code null} if it couldn't be normalized
     */
    static LocalDate normalizeDate(String raw) {
        String cleaned = collapseWhitespace(raw);
        if (isPlaceholder(cleaned)) return null;

        try {
            Matcher m = ISO_DATE_PATTERN.matcher(cleaned);
            if (m.matches()) {
                return LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
            }
            m = SLASH_MDY_PATTERN.matcher(cleaned);
            if (m.matches()) {
                return LocalDate.of(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
            }
            m = DASH_DMY_PATTERN.matcher(cleaned);
            if (m.matches()) {
                return LocalDate.of(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
            }
        } catch (java.time.DateTimeException e) {
            // matched a known shape but described a calendar-invalid date (e.g. Feb 30)
        }
        return null; // unrecognized format
    }

    
    
    /** Shared string helpers:
     * 
     * @return true if {@code cleaned} (already trimmed/collapsed) is a known "no data" placeholder, case-insensitively 
    */
    private static boolean isPlaceholder(String cleaned) {
        return PLACEHOLDER_VALUES.contains(cleaned.toLowerCase());
    }

    /**
     * Trims leading/trailing whitespace and collapses any run of internal
     * whitespace (including double spaces) down to a single space.
     *
     * @param raw raw field value, may be {@code null}
     * @return the cleaned string, or {@code ""} if {@code raw} was {@code null}
     */
    private static String collapseWhitespace(String raw) {
        if (raw == null) return "";
        return raw.trim().replaceAll("\\s+", " ");
    }

    /**
     * Title-cases a string word by word, treating hyphens as word boundaries
     * so both sides of a hyphenated word are capitalized
     * (e.g. {@code "kwa-zulu"} -> {@code "Kwa-Zulu"}).
     *
     * @param s already-trimmed/whitespace-collapsed string
     * @return the title-cased string
     */
    private static String toTitleCase(String s) {
        if (s.isEmpty()) return s;
        String[] words = s.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(" ");
            String[] parts = w.split("-");
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) sb.append("-");
                if (!parts[i].isEmpty()) {
                    sb.append(Character.toUpperCase(parts[i].charAt(0)))
                      .append(parts[i].substring(1).toLowerCase());
                }
            }
        }
        return sb.toString();
    }
}