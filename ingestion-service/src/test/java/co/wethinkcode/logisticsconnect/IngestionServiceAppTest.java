package co.wethinkcode.logisticsconnect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;


/**
 * IngestionServiceAppTest
 */
public class IngestionServiceAppTest {
    /* normalizeHubId tests */
    @Nested
    @DisplayName("normalizeHubId")
    class NormalizeHubIdTests {

        @Test
        void normalizeHubIdTrimsANdUppercases() {
            assertEquals("H-501", IngestionServiceApp.normalizeHubId(" h-501 "));
        }

        @Test
        void leavesAlreadyCleanIdUnchanged() {
            assertEquals("H-500", IngestionServiceApp.normalizeHubId("H-500"));
        }

        @Test
        void handlesNullAsEmptyString() {
            assertEquals("", IngestionServiceApp.normalizeHubId(null));
        }
    
    }
/*Normalize province tests*/
    @Nested
    @DisplayName("normalizeProvince")
    class NormalizeProvinceTests {

        @Test
        void mapsLowercaseToCanonicalTitleCase() {
            assertEquals("Gauteng", IngestionServiceApp.normalizeProvince("gauteng"));
        }

        @Test
        void trimsPadding() {
            assertEquals("Western Cape", IngestionServiceApp.normalizeProvince(" Western Cape "));
        }

        @Test
        void collapsesAllKwaZuluNatalSpellingVariantsToOneCanonicalForm() {
            assertEquals("KwaZulu-Natal", IngestionServiceApp.normalizeProvince("KwaZulu-Natal"));
            assertEquals("KwaZulu-Natal", IngestionServiceApp.normalizeProvince("Kwa-Zulu Natal"));
            assertEquals("KwaZulu-Natal", IngestionServiceApp.normalizeProvince("KwaZulu Natal"));
            assertEquals("KwaZulu-Natal", IngestionServiceApp.normalizeProvince("kwa zulu Natal"));
        }

        @Test
        void blankProvinceIsNullNotEmptyString() {
            assertNull(IngestionServiceApp.normalizeProvince(""));
            assertNull(IngestionServiceApp.normalizeProvince("  "));
        }

        @Test
        void placeholderValuesAreNull() {
            assertNull(IngestionServiceApp.normalizeProvince("N/A"));
            assertNull(IngestionServiceApp.normalizeProvince("unknown"));
            assertNull(IngestionServiceApp.normalizeProvince("TBD"));
        }

        @Test
        void unrecognizedProvinceFallsBackToTitleCaseRatherThanFailing() {
            assertEquals("Some New Province", IngestionServiceApp.normalizeProvince("some new province"));
        }

    }
/* Sorting center normalizes text tests*/
    @Nested
    @DisplayName("normalizeText")
    class NormalizeTextTests {
        @Test
        void collapsesInternalDoubleSpaces() {
            assertEquals("Cape Town Port", IngestionServiceApp.normalizeText("Cape Town  Port"));
        }

        @Test
        void trimsPadding() {
            assertEquals("Pretoria North", IngestionServiceApp.normalizeText(" Pretoria North "));
        }

        @Test
        void titleCasesInconsistentCasing() {
            assertEquals("Johannesburg Central", IngestionServiceApp.normalizeText("johannesburg central"));
        }

        @Test
        void placeholderBecomesEmptyString() {
            assertEquals("", IngestionServiceApp.normalizeText("N/A"));
        }
    }
/*Parse Boolean  tests*/
    @Nested
    @DisplayName("parseBoolean")
    class ParseBooleanTests {
        
        @Test
        void recognizesAllTrueVariantsCaseInsensitively() {
            for (String v : List.of("Y", "yes", "true", "TRUE", "1")) {
                assertEquals(Boolean.TRUE, IngestionServiceApp.parseBoolean(v), "expected true for: " + v);
            }
        }

        @Test
        void recognizesAllFalseVariantsCaseInsensitively() {
            for (String v : List.of("N", "no", "false", "FALSE", "0")) {
                assertEquals(Boolean.FALSE, IngestionServiceApp.parseBoolean(v), "expected false for: " + v);
            }
        }

        @Test
        void placeholderAreNullNotFalse() {
            assertNull(IngestionServiceApp.parseBoolean("N/A"));
            assertNull(IngestionServiceApp.parseBoolean("unknown"));
            assertNull(IngestionServiceApp.parseBoolean(""));
        }

        @Test
        void unrecognizedValueIsNullRatherThanThrowing() {
            assertNull(IngestionServiceApp.parseBoolean("maybe"));
        }
    }
/* Normalize date tests */
    @Nested
    @DisplayName("normalizeDate")
    class NormalizeDateTests {

        @Test 
        void parseIsoFormatIncludingOneDigitMonthAndDAy() {
            assertEquals(LocalDate.of(2026, 7, 18), IngestionServiceApp.normalizeDate("2026-7-18"));
        }

        @Test
        void parsesSlashMdyFormat() {
            assertEquals(LocalDate.of(2026, 7, 18), IngestionServiceApp.normalizeDate("07/18/2026"));
        }

        @Test
        void parsesDashDmyFormat() {
            assertEquals(LocalDate.of(2026, 7, 18), IngestionServiceApp.normalizeDate("18-07-2026"));
        }

        @Test
        void rejectsCalendarInvalidDateInsteadOfRollingItOver() {
            assertNull(IngestionServiceApp.normalizeDate("2023-02-30"));
        }

        @Test
        void placeholderIsNull() {
            assertNull(IngestionServiceApp.normalizeDate("TBD"));
        }

        @Test 
        void unrecognizedShapeIsNullRatherThanThrowing() {
            assertNull(IngestionServiceApp.normalizeDate("next Tuesday"));
        }
    }

/* Dedupe / resolveActive tests */
    @Nested
    @DisplayName("dedupe")
    class DedupeTests {
        private HubRecord record(String id, String province, String sortingCenter, Boolean active) {
            return new HubRecord(id, province, sortingCenter, active, new ArrayList<>());
        }

        @Test
        void groupsRecordsBySortingCenterRegardlessOfHubIdOrCasing() {
            List<HubRecord> input = List.of(
                record("H-500", "Gauteng", "Johannesburg Central", true),
                record("H-504", "Gauteng", "Johannesburg Central", true),
                record("H-510", "Gauteng", "johannesburg central", false)
            );
            List<HubRecord> result = IngestionServiceApp.dedupe(input);
            assertEquals(1, result.size());
        }

        @Test
        void picksLowestNumberedHubIdAsCanonical() {
            List<HubRecord> input = List.of(
                record("H-510", "Gauteng", "Johannesburg Central", true),
                record("H-500", "Gauteng", "Johannesburg Central", true),
                record("H-515", "Gauteng", "Johannesburg Central", true)
            );
            HubRecord result = IngestionServiceApp.dedupe(input).get(0);
            assertEquals("H-500", result.getHubId());
            assertTrue(result.getMergedFrom().containsAll(List.of("H-510", "H-515")));
        }

        @Test
        void activeIsResolvedByMajorityVote() {
            List<HubRecord> input = List.of(
                record("H-500", "Gauteng", "Johannesburg Central", true),
                record("H-504", "Gauteng", "Johannesburg Central", true),
                record("H-510", "Gauteng", "Johannesburg Central", false)
            );
        }

        @Test 
        void tiedActiveVoteResolvesToTrue() {
            List<HubRecord> input = List.of(
                record("H-502", "Gauteng", "Pretoria North", false),
                record("H-508", null, "Pretoria North", true)
            );

            HubRecord result = IngestionServiceApp.dedupe(input).get(0);
            assertEquals(Boolean.TRUE, result.getActive());
        }

        @Test
        void missingProvinceIsbackFilledFromAnotherMemberOfTheGroup() {
            List<HubRecord> input = List.of(
                record("H-502", "Gauteng", "Pretoria North", false),
                record("H-508", null, "Pretoria North", true)
            );
            HubRecord result = IngestionServiceApp.dedupe(input).get(0);
            assertEquals("Gauteng", result.getProvince());
        }

        @Test
        void allPlaceholderActiveValuesInAGroupResolveToNull() {
            List<HubRecord> input = List.of(
                record("H-511", "Limpopo", "Polokwane Hub", null)
            );
            HubRecord result = IngestionServiceApp.dedupe(input).get(0);
            assertNull(result.getActive());
        }

        @Test
        void nonDuplicateRecordsPassThroughUnmerged() {
            List<HubRecord> input = List.of(
                record("H-507", "Free State", "Bloemfontein Hub", false),
                record("H-513", "North West", "Rustenburg Hub", false)
            );

            List<HubRecord> result = IngestionServiceApp.dedupe(input);

            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(r -> r.getMergedFrom().isEmpty()));
        }
    }
/* End to end tests for loading and cleaning the actual hubs-gloval.csv resource */
    @Nested
    @DisplayName( "loadAndCleanHubs (integration)")
    class LoadAndCleanHubsTests {
        @Test
        void collaspesNineteenRawRowsToTenDeduplicatedHubs() throws IOException {
            List<HubRecord> hubs = IngestionServiceApp.loadAndCleanHubs();
            assertEquals(10, hubs.size());

        }
        
       @Test
        void everyRecordhasCleanNonPlaceholderFields() throws IOException {
            List<HubRecord> hubs = IngestionServiceApp.loadAndCleanHubs();

            for (HubRecord hub : hubs) {
                assertFalse(hub.getHubId().isEmpty(), "empty hubId in: " + hub.getHubId());
                assertFalse(hub.getSortingCenter().isEmpty(), "empty sortingCenter in hub: " + hub.getHubId());
                assertFalse(hub.getHubId().contains("  "), "double space in hubId: [" + hub.getHubId() + "]");
                assertFalse(hub.getSortingCenter().contains("  "), "double space in sortingCenter: [" + hub.getSortingCenter() + "] (hub " + hub.getHubId() + ")");
             }
        }

        @Test
        void johannesburgCentralGroupMergesToH500WithMajorityTrueActive() throws IOException {
            List<HubRecord> hubs = IngestionServiceApp.loadAndCleanHubs();

            Map<String, HubRecord> byId = hubs.stream().collect(java.util.stream.Collectors.toMap(HubRecord::getHubId, h -> h));

            HubRecord jhb = byId.get("H-500");
            assertNotNull(jhb, "expected H-500 to be the canonical Johannesburg Central record");
            assertEquals("Johannesburg Central", jhb.getSortingCenter());
            assertEquals("Gauteng", jhb.getProvince());
            assertEquals(Boolean.TRUE, jhb.getActive());
            assertTrue(jhb.getMergedFrom().containsAll(List.of("H-504", "H-510", "H-515")));
        }


        @Test
        void pretoriaNorthGroupBackfillsProvinceAndBreaksActiveTieToTrue() throws IOException {
            List<HubRecord> hubs = IngestionServiceApp.loadAndCleanHubs();

            HubRecord pretoria = hubs.stream()
                    .filter(h -> h.getSortingCenter().equals("Pretoria North"))
                    .findFirst()
                    .orElseThrow();

            assertEquals("H-502", pretoria.getHubId());
            assertEquals("Gauteng", pretoria.getProvince());
            assertEquals(Boolean.TRUE, pretoria.getActive());
        }

        @Test
        void durbanHarbourGroupCollapsesAllKwaZuluNatalSpellingVariants() throws IOException {
            List<HubRecord> hubs = IngestionServiceApp.loadAndCleanHubs();

            HubRecord durban = hubs.stream()
                    .filter(h -> h.getSortingCenter().equals("Durban Harbour"))
                    .findFirst()
                    .orElseThrow();

            assertEquals("KwaZulu-Natal", durban.getProvince());
        }

         @Test
        void recordWithOnlyPlaceholderActiveValueStaysNull() throws IOException {
            List<HubRecord> hubs = IngestionServiceApp.loadAndCleanHubs();

            HubRecord polokwane = hubs.stream()
                    .filter(h -> h.getSortingCenter().equals("Polokwane Hub"))
                    .findFirst()
                    .orElseThrow();

            assertNull(polokwane.getActive());
        }

    }
}