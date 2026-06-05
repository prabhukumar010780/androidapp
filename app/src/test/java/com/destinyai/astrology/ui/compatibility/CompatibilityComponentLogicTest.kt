package com.destinyai.astrology.ui.compatibility

import com.destinyai.astrology.domain.model.AnalysisStep
import com.destinyai.astrology.domain.model.ComparisonResult
import com.destinyai.astrology.domain.model.KutaDetail
import com.destinyai.astrology.domain.model.MangalDoshaModel
import com.destinyai.astrology.domain.model.PartnerData
import com.destinyai.astrology.domain.model.YogaItem
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for pure logic extracted from new compatibility components.
 */
class CompatibilityComponentLogicTest {

    // ── PartnerAvatarInitial ──────────────────────────────────────────────────

    @Test
    fun `partnerInitial returns uppercase first character`() {
        assertEquals("P", partnerInitial("Prabhu"))
    }

    @Test
    fun `partnerInitial handles empty string`() {
        assertEquals("", partnerInitial(""))
    }

    @Test
    fun `partnerInitial returns only one character`() {
        assertEquals("A", partnerInitial("Asma"))
    }

    // ── PartnerData.isComplete ───────────────────────────────────────────────────

    @Test
    fun `PartnerData isComplete true when name and city provided without coordinates`() {
        val partner = com.destinyai.astrology.domain.model.PartnerData(
            name = "Priya",
            city = "Mumbai",
            latitude = 0.0,
            longitude = 0.0,
        )
        assertTrue(partner.isComplete)
    }

    @Test
    fun `PartnerData isComplete false when name is blank`() {
        val partner = com.destinyai.astrology.domain.model.PartnerData(
            name = "",
            city = "Mumbai",
            latitude = 0.0,
            longitude = 0.0,
        )
        assertFalse(partner.isComplete)
    }

    @Test
    fun `PartnerData isComplete false when city is blank`() {
        val partner = com.destinyai.astrology.domain.model.PartnerData(
            name = "Priya",
            city = "",
            latitude = 0.0,
            longitude = 0.0,
        )
        assertFalse(partner.isComplete)
    }

    @Test
    fun `AnalysisStep CALCULATING_CHARTS has lowest ordinal`() {
        assertTrue(AnalysisStep.CALCULATING_CHARTS.ordinal < AnalysisStep.COMPLETE.ordinal)
    }

    @Test
    fun `AnalysisStep COMPLETE has highest ordinal`() {
        val max = AnalysisStep.values().maxByOrNull { it.ordinal }
        assertEquals(AnalysisStep.COMPLETE, max)
    }

    @Test
    fun `stepIsCompleted returns true when step ordinal is less than current`() {
        assertTrue(stepIsCompleted(AnalysisStep.CALCULATING_CHARTS, AnalysisStep.MANGAL_DOSHA))
    }

    @Test
    fun `stepIsCompleted returns false when step ordinal equals current`() {
        assertFalse(stepIsCompleted(AnalysisStep.MANGAL_DOSHA, AnalysisStep.MANGAL_DOSHA))
    }

    @Test
    fun `stepIsCompleted returns false when step ordinal is greater than current`() {
        assertFalse(stepIsCompleted(AnalysisStep.GENERATING_ANALYSIS, AnalysisStep.MANGAL_DOSHA))
    }

    // ── multiPartnerProgressFraction ─────────────────────────────────────────

    @Test
    fun `progressFraction is 0 when no completions`() {
        assertEquals(0f, multiPartnerProgressFraction(completed = 0, total = 3))
    }

    @Test
    fun `progressFraction is 1 when all complete`() {
        assertEquals(1f, multiPartnerProgressFraction(completed = 3, total = 3))
    }

    @Test
    fun `progressFraction is 0 when total is 0`() {
        assertEquals(0f, multiPartnerProgressFraction(completed = 0, total = 0))
    }

    @Test
    fun `progressFraction returns partial fraction`() {
        assertEquals(0.5f, multiPartnerProgressFraction(completed = 1, total = 2))
    }

    // ── shareCardStarCount ────────────────────────────────────────────────────

    @Test
    fun `shareCardStarCount returns 5 for 90+ percent`() {
        assertEquals(5, shareCardStarCount(isRecommended = true, percentage = 0.92))
    }

    @Test
    fun `shareCardStarCount returns 4 for 75-89 percent`() {
        assertEquals(4, shareCardStarCount(isRecommended = true, percentage = 0.80))
    }

    @Test
    fun `shareCardStarCount returns 3 for 60-74 percent`() {
        assertEquals(3, shareCardStarCount(isRecommended = true, percentage = 0.65))
    }

    @Test
    fun `shareCardStarCount returns 1 when not recommended`() {
        assertEquals(1, shareCardStarCount(isRecommended = false, percentage = 0.95))
    }

    // ── firstNameFrom ─────────────────────────────────────────────────────────

    @Test
    fun `firstNameFrom returns first word of full name`() {
        assertEquals("Prabhu", firstNameFrom("Prabhu Kumar"))
    }

    @Test
    fun `firstNameFrom returns the name when no space`() {
        assertEquals("Asma", firstNameFrom("Asma"))
    }

    @Test
    fun `firstNameFrom returns empty for empty string`() {
        assertEquals("", firstNameFrom(""))
    }

    // ── partnerTabLabel ───────────────────────────────────────────────────────

    @Test
    fun `partnerTabLabel returns Partner 1 for index 0`() {
        assertEquals("Partner 1", partnerTabLabel(0))
    }

    @Test
    fun `partnerTabLabel returns Partner 2 for index 1`() {
        assertEquals("Partner 2", partnerTabLabel(1))
    }

    @Test
    fun `partnerTabLabel returns Partner 3 for index 2`() {
        assertEquals("Partner 3", partnerTabLabel(2))
    }

    // ── buildComparisonExportText ─────────────────────────────────────────────

    private fun makeResult(name: String, adjusted: Int, total: Int, max: Int, recommended: Boolean) =
        ComparisonResult(
            partner = PartnerData(name = name),
            totalScore = total,
            maxScore = max,
            overallScore = total,
            isRecommended = recommended,
            adjustedScore = adjusted,
            summary = "",
        )

    @Test
    fun `buildComparisonExportText includes user name header`() {
        val text = buildComparisonExportText("Prabhu", emptyList())
        assertTrue(text.contains("Prabhu"))
    }

    @Test
    fun `buildComparisonExportText lists partner name and score`() {
        val result = makeResult("Priya", adjusted = 28, total = 28, max = 36, recommended = true)
        val text = buildComparisonExportText("Prabhu", listOf(result))
        assertTrue(text.contains("Priya"))
        assertTrue(text.contains("28"))
    }

    @Test
    fun `buildComparisonExportText marks recommended partner`() {
        val result = makeResult("Priya", adjusted = 28, total = 28, max = 36, recommended = true)
        val text = buildComparisonExportText("Prabhu", listOf(result))
        assertTrue(text.contains("✓") || text.contains("Recommended"))
    }

    @Test
    fun `buildComparisonExportText includes best match line when recommended exists`() {
        val result = makeResult("Priya", adjusted = 28, total = 28, max = 36, recommended = true)
        val text = buildComparisonExportText("Prabhu", listOf(result))
        assertTrue(text.contains("Best match") || text.contains("Best Match"))
    }

    @Test
    fun `buildComparisonExportText omits best match line when no recommended result`() {
        val result = makeResult("Priya", adjusted = 10, total = 10, max = 36, recommended = false)
        val text = buildComparisonExportText("Prabhu", listOf(result))
        assertFalse(text.lowercase().contains("best match"))
    }

    @Test
    fun `buildComparisonExportText includes app attribution footer`() {
        val text = buildComparisonExportText("Prabhu", emptyList())
        assertTrue(text.contains("Destiny AI"))
    }

    // ── History search includes userName ─────────────────────────────────────

    @Test
    fun `historySearchFilter matches boyName`() {
        assertTrue(historySearchFilter("Prabhu", boyName = "Prabhu", girlName = "Priya", userName = "Other"))
    }

    @Test
    fun `historySearchFilter matches girlName`() {
        assertTrue(historySearchFilter("Priya", boyName = "Prabhu", girlName = "Priya", userName = "Other"))
    }

    @Test
    fun `historySearchFilter matches userName`() {
        assertTrue(historySearchFilter("User", boyName = "Prabhu", girlName = "Priya", userName = "User Name"))
    }

    @Test
    fun `historySearchFilter is case insensitive`() {
        assertTrue(historySearchFilter("prabhu", boyName = "Prabhu", girlName = "Priya", userName = "Other"))
    }

    @Test
    fun `historySearchFilter returns false when no match`() {
        assertFalse(historySearchFilter("xyz", boyName = "Prabhu", girlName = "Priya", userName = "Other"))
    }

    @Test
    fun `historySearchFilter returns true on blank query`() {
        assertTrue(historySearchFilter("", boyName = "Prabhu", girlName = "Priya", userName = "Other"))
    }

    // ── History user-message count ────────────────────────────────────────────

    @Test
    fun `userMessageCount counts only user messages`() {
        val messages = listOf(
            com.destinyai.astrology.domain.model.CompatChatMessageData(content = "hi", isUser = true),
            com.destinyai.astrology.domain.model.CompatChatMessageData(content = "response", isUser = false),
            com.destinyai.astrology.domain.model.CompatChatMessageData(content = "follow up", isUser = true),
        )
        assertEquals(2, userMessageCount(messages))
    }

    @Test
    fun `userMessageCount returns 0 when empty`() {
        assertEquals(0, userMessageCount(emptyList()))
    }

    // ── MangalDosha localizeExceptionKey ─────────────────────────────────────

    @Test
    fun `localizeExceptionKey returns readable label for known key`() {
        val label = localizeExceptionKey("same_dosha_match")
        assertFalse(label.contains("_"))
    }

    @Test
    fun `localizeExceptionKey returns original key when unknown`() {
        val label = localizeExceptionKey("unknown_key_xyz")
        assertTrue(label.isNotBlank())
    }

    // ── Kalsarpa doshaDescription ─────────────────────────────────────────────

    @Test
    fun `kalsarpaDoshaDescription returns non-blank for all 12 yoga names`() {
        val yogaNames = listOf(
            "Anant", "Kulik", "Vasuki", "Shankhpal", "Padma", "Mahapadma",
            "Takshak", "Karkotak", "Shankhachud", "Ghatak", "Vishdhar", "Sheshnag"
        )
        yogaNames.forEach { name ->
            val desc = kalsarpaDoshaDescription(name)
            assertTrue(desc.isNotBlank(), "Expected non-blank for $name")
        }
    }

    @Test
    fun `kalsarpaDoshaDescription returns fallback for unknown name`() {
        val desc = kalsarpaDoshaDescription("UnknownYoga")
        assertTrue(desc.isNotBlank())
    }

    // ── yogasSortedAlphabetically ─────────────────────────────────────────────

    private fun makeYogaItem(name: String, isActive: Boolean = true) = YogaItem(
        id = name,
        name = name,
        yogaKey = name.lowercase(),
        status = if (isActive) "A" else "C",
        strengthRaw = if (isActive) 80.0 else 0.0,
        description = "",
    )

    @Test
    fun `yogasSortedAlphabetically returns items sorted by displayName`() {
        val items = listOf(makeYogaItem("Zebra"), makeYogaItem("Apple"), makeYogaItem("Mango"))
        val sorted = yogasSortedAlphabetically(items)
        assertEquals(listOf("Apple", "Mango", "Zebra"), sorted.map { it.name })
    }

    @Test
    fun `yogasSortedAlphabetically returns empty list for empty input`() {
        assertTrue(yogasSortedAlphabetically(emptyList()).isEmpty())
    }

    @Test
    fun `yogasSortedAlphabetically is case insensitive`() {
        val items = listOf(makeYogaItem("zebra"), makeYogaItem("Apple"))
        val sorted = yogasSortedAlphabetically(items)
        assertEquals("Apple", sorted.first().name)
    }

    // ── resultScreenSubtitle ─────────────────────────────────────────────────

    @Test
    fun `resultScreenSubtitle includes both city names when provided`() {
        val subtitle = resultScreenSubtitle(boyCity = "Bhilai", girlCity = "Mumbai")
        assertTrue(subtitle.contains("Bhilai"))
        assertTrue(subtitle.contains("Mumbai"))
    }

    @Test
    fun `resultScreenSubtitle returns empty when both cities blank`() {
        val subtitle = resultScreenSubtitle(boyCity = "", girlCity = "")
        assertTrue(subtitle.isBlank())
    }

    @Test
    fun `resultScreenSubtitle returns single city when only one provided`() {
        val subtitle = resultScreenSubtitle(boyCity = "Delhi", girlCity = "")
        assertTrue(subtitle.contains("Delhi"))
    }

    // ── compatibilityScoreLabel ───────────────────────────────────────────────

    @Test
    fun `compatibilityScoreLabel formats as score slash maxScore`() {
        assertEquals("28/36", compatibilityScoreLabel(28, 36))
    }

    @Test
    fun `compatibilityScoreLabel handles zero score`() {
        assertEquals("0/36", compatibilityScoreLabel(0, 36))
    }

    @Test
    fun `compatibilityScoreLabel handles full score`() {
        assertEquals("36/36", compatibilityScoreLabel(36, 36))
    }

    // ── adjustedScoreNote ─────────────────────────────────────────────────────

    @Test
    fun `adjustedScoreNote returns Raw dot Adjusted format`() {
        val note = adjustedScoreNote(totalScore = 25, maxScore = 36, adjustedScore = 28)
        assertEquals("Raw: 25/36 · Adjusted: 28", note)
    }

    @Test
    fun `adjustedScoreNote uses raw score and adjusted score`() {
        val note = adjustedScoreNote(totalScore = 18, maxScore = 36, adjustedScore = 22)
        assertTrue(note.contains("18/36"))
        assertTrue(note.contains("22"))
    }

    // ── formattedUserSummary ──────────────────────────────────────────────────

    @Test
    fun `formattedUserSummary joins all non-empty fields with dot separator`() {
        val summary = formattedUserSummary(
            name = "Prabhu",
            gender = "male",
            dob = "1980-07-01",
            time = "06:30",
            city = "Bhilai",
            timeUnknown = false,
        )
        assertEquals("Prabhu · Male · 1980-07-01 · 06:30 · Bhilai", summary)
    }

    @Test
    fun `formattedUserSummary omits time when timeUnknown is true`() {
        val summary = formattedUserSummary(
            name = "Prabhu",
            gender = "male",
            dob = "1980-07-01",
            time = "06:30",
            city = "Bhilai",
            timeUnknown = true,
        )
        assertFalse(summary.contains("06:30"))
        assertTrue(summary.contains("Prabhu · Male · 1980-07-01 · Bhilai"))
    }

    @Test
    fun `formattedUserSummary omits empty name`() {
        val summary = formattedUserSummary(
            name = "",
            gender = "female",
            dob = "1990-01-15",
            time = "",
            city = "Delhi",
            timeUnknown = false,
        )
        assertFalse(summary.startsWith(" ·"))
        assertTrue(summary.startsWith("Female"))
    }

    @Test
    fun `formattedUserSummary omits empty gender`() {
        val summary = formattedUserSummary(
            name = "Asha",
            gender = "",
            dob = "1990-01-15",
            time = "09:00",
            city = "Pune",
            timeUnknown = false,
        )
        assertFalse(summary.contains(" ·  ·"))
        assertEquals("Asha · 1990-01-15 · 09:00 · Pune", summary)
    }

    @Test
    fun `formattedUserSummary omits empty city`() {
        val summary = formattedUserSummary(
            name = "Ravi",
            gender = "male",
            dob = "1985-05-20",
            time = "10:00",
            city = "",
            timeUnknown = false,
        )
        assertFalse(summary.endsWith("·"))
        assertFalse(summary.contains("·  "))
    }

    // ── ageBlockMessage ───────────────────────────────────────────────────────

    @Test
    fun `ageBlockMessage returns null when both ages are 18 or older`() {
        assertNull(ageBlockMessage(userDob = "1990-01-01", partnerDob = "1995-06-15", today = "2026-05-27"))
    }

    @Test
    fun `ageBlockMessage returns message when user is under 18`() {
        val msg = ageBlockMessage(userDob = "2015-01-01", partnerDob = "1995-06-15", today = "2026-05-27")
        assertNotNull(msg)
        assertTrue(msg!!.isNotBlank())
    }

    @Test
    fun `ageBlockMessage returns message when partner is under 18`() {
        val msg = ageBlockMessage(userDob = "1990-01-01", partnerDob = "2015-06-15", today = "2026-05-27")
        assertNotNull(msg)
        assertTrue(msg!!.isNotBlank())
    }

    @Test
    fun `ageBlockMessage returns null when partner dob is empty`() {
        assertNull(ageBlockMessage(userDob = "1990-01-01", partnerDob = "", today = "2026-05-27"))
    }

    @Test
    fun `ageBlockMessage returns null when user dob is empty`() {
        assertNull(ageBlockMessage(userDob = "", partnerDob = "1990-01-01", today = "2026-05-27"))
    }

    // ── rejectionReasonPrefix ─────────────────────────────────────────────────

    @Test
    fun `rejectionReasonPrefix extracts Nadi Dosha is active prefix`() {
        val reason = "Nadi Dosha is active — same biological constitution. Highly discouraged."
        assertEquals("Nadi Dosha is active", rejectionReasonPrefix(reason))
    }

    @Test
    fun `rejectionReasonPrefix extracts Bhakoot Dosha is active prefix`() {
        val reason = "Bhakoot Dosha is active — Moon positions create friction."
        assertEquals("Bhakoot Dosha is active", rejectionReasonPrefix(reason))
    }

    @Test
    fun `rejectionReasonPrefix extracts Mangal Dosha incompatibility prefix`() {
        val reason = "Mangal Dosha incompatibility — Prabhu: Severe (Mars in H7), Priya: None."
        assertEquals("Mangal Dosha incompatibility", rejectionReasonPrefix(reason))
    }

    @Test
    fun `rejectionReasonPrefix extracts Adjusted Ashtakoot score prefix`() {
        val reason = "Adjusted Ashtakoot score 15/36 — below the 18-point minimum threshold."
        assertEquals("Adjusted Ashtakoot score", rejectionReasonPrefix(reason))
    }

    @Test
    fun `rejectionReasonPrefix returns null for unknown reason`() {
        assertNull(rejectionReasonPrefix("Some other reason without known prefix"))
    }

    // ── fallbackCancelledDoshaText ────────────────────────────────────────────

    @Test
    fun `fallbackCancelledDoshaText builds single dosha name`() {
        val details = mapOf("nadi" to true, "bhakoot" to false)
        val text = fallbackCancelledDoshaText(cancelledDetails = details, cancelledCount = 1)
        assertTrue(text.contains("Nadi"))
        assertTrue(text.contains("cancelled"))
    }

    @Test
    fun `fallbackCancelledDoshaText builds two dosha names joined with and`() {
        val details = mapOf("nadi" to true, "bhakoot" to true, "gana" to false)
        val text = fallbackCancelledDoshaText(cancelledDetails = details, cancelledCount = 2)
        assertTrue(text.contains("Nadi"))
        assertTrue(text.contains("Bhakoot"))
        assertTrue(text.contains("and"))
    }

    @Test
    fun `fallbackCancelledDoshaText falls back to count when no matching keys`() {
        val text = fallbackCancelledDoshaText(cancelledDetails = emptyMap(), cancelledCount = 3)
        assertTrue(text.contains("3"))
    }

    // ── followUpResponseStatus ────────────────────────────────────────────────

    @Test
    fun `followUpResponseStatus returns success for success status`() {
        assertEquals(FollowUpResponseStatus.SUCCESS, followUpResponseStatus("success"))
    }

    @Test
    fun `followUpResponseStatus returns redirect for redirect status`() {
        assertEquals(FollowUpResponseStatus.REDIRECT, followUpResponseStatus("redirect"))
    }

    @Test
    fun `followUpResponseStatus returns blocked for blocked status`() {
        assertEquals(FollowUpResponseStatus.BLOCKED, followUpResponseStatus("blocked"))
    }

    @Test
    fun `followUpResponseStatus returns error for unknown status`() {
        assertEquals(FollowUpResponseStatus.ERROR, followUpResponseStatus("unknown"))
    }

    @Test
    fun `followUpResponseStatus returns error for null`() {
        assertEquals(FollowUpResponseStatus.ERROR, followUpResponseStatus(null))
    }

    // ── followUpDisplayAnswer ──────────────────────────────────────────────────

    @Test
    fun `followUpDisplayAnswer returns answer for success`() {
        val answer = followUpDisplayAnswer(
            status = "success",
            answer = "They are compatible.",
            message = null,
        )
        assertEquals("They are compatible.", answer)
    }

    @Test
    fun `followUpDisplayAnswer returns message for blocked`() {
        val answer = followUpDisplayAnswer(
            status = "blocked",
            answer = null,
            message = "This topic is outside astrology scope.",
        )
        assertEquals("This topic is outside astrology scope.", answer)
    }

    @Test
    fun `followUpDisplayAnswer returns fallback when answer and message are null`() {
        val answer = followUpDisplayAnswer(status = "error", answer = null, message = null)
        assertTrue(answer.isNotBlank())
    }

    // ── followUpSuggestionsToDisplay ──────────────────────────────────────────

    @Test
    fun `followUpSuggestionsToDisplay returns api suggestions when non-empty`() {
        val apiSuggestions = listOf("How is Nadi?", "What about Mangal?")
        val result = followUpSuggestionsToDisplay(
            apiSuggestions = apiSuggestions,
            defaultSuggestions = listOf("Default 1", "Default 2"),
        )
        assertEquals(apiSuggestions, result)
    }

    @Test
    fun `followUpSuggestionsToDisplay returns defaults when api suggestions empty`() {
        val defaults = listOf("Default 1", "Default 2")
        val result = followUpSuggestionsToDisplay(
            apiSuggestions = emptyList(),
            defaultSuggestions = defaults,
        )
        assertEquals(defaults, result)
    }

    @Test
    fun `followUpSuggestionsToDisplay caps at 4 items`() {
        val apiSuggestions = listOf("A", "B", "C", "D", "E")
        val result = followUpSuggestionsToDisplay(
            apiSuggestions = apiSuggestions,
            defaultSuggestions = emptyList(),
        )
        assertEquals(4, result.size)
    }

    // ── comparisonReasonText with allResults ──────────────────────────────────

    private fun makeCompResult(
        name: String,
        adjusted: Int,
        overall: Int,
        max: Int = 36,
        recommended: Boolean = true,
    ) = ComparisonResult(
        partner = PartnerData(name = name),
        totalScore = overall,
        maxScore = max,
        overallScore = overall,
        isRecommended = recommended,
        adjustedScore = adjusted,
        summary = "",
    )

    @Test
    fun `comparisonReasonText includes delta when best scores higher than second recommended`() {
        val best = makeCompResult("Priya", adjusted = 28, overall = 28)
        val second = makeCompResult("Asha", adjusted = 22, overall = 22)
        val text = comparisonReasonText(best, allResults = listOf(best, second))
        assertTrue(text.contains("6") || text.contains("Asha"), "Expected delta or competitor name in: $text")
    }

    @Test
    fun `comparisonReasonText notes only viable match when all others rejected`() {
        val best = makeCompResult("Priya", adjusted = 28, overall = 28)
        val rejected = makeCompResult("Asha", adjusted = 22, overall = 22, recommended = false)
        val text = comparisonReasonText(best, allResults = listOf(best, rejected))
        assertTrue(text.contains("viable") || text.contains("disqualified") || text.contains("dosha"), "Expected rejection note in: $text")
    }

    @Test
    fun `comparisonReasonText returns non-blank for single result list`() {
        val best = makeCompResult("Priya", adjusted = 28, overall = 28)
        val text = comparisonReasonText(best, allResults = listOf(best))
        // Single result — falls back to basic info, must be non-blank
        assertTrue(text.isNotBlank())
    }

    @Test
    fun `comparisonReasonText notes rejection note when rejected partner outscored winner`() {
        val best = makeCompResult("Priya", adjusted = 25, overall = 25)
        val rejected = makeCompResult("Asha", adjusted = 30, overall = 30, recommended = false)
        val text = comparisonReasonText(best, allResults = listOf(best, rejected))
        assertTrue(text.contains("Asha") || text.contains("disqualified") || text.contains("dosha"), "Expected rejection note for higher-scoring rejected: $text")
    }

    // ── stripFollowUpSection ──────────────────────────────────────────────────

    @Test
    fun `stripFollowUpSection removes follow-up marker and everything after`() {
        val text = "Some analysis\n### 💬 SUGGESTED FOLLOW-UP QUESTIONS\nQ1\nQ2"
        val stripped = stripFollowUpSection(text)
        assertFalse(stripped.contains("SUGGESTED FOLLOW-UP"))
        assertFalse(stripped.contains("Q1"))
        assertTrue(stripped.contains("Some analysis"))
    }

    @Test
    fun `stripFollowUpSection returns text unchanged when no marker present`() {
        val text = "Some analysis without follow-up section"
        assertEquals(text, stripFollowUpSection(text))
    }

    @Test
    fun `stripFollowUpSection strips trailing dashes after removal`() {
        val text = "Some analysis\n---\n### 💬 SUGGESTED FOLLOW-UP QUESTIONS\nQ1"
        val stripped = stripFollowUpSection(text)
        assertFalse(stripped.endsWith("---"))
    }

    // ── extractFinalRecommendation ────────────────────────────────────────────

    @Test
    fun `extractFinalRecommendation returns text after FINAL RECOMMENDATION header`() {
        val text = "Intro\n\nFINAL RECOMMENDATION\nPriya is the best match."
        val extracted = extractFinalRecommendation(text)
        assertTrue(extracted.contains("Priya is the best match."))
        assertFalse(extracted.contains("Intro"))
    }

    @Test
    fun `extractFinalRecommendation returns full text when no header present`() {
        val text = "No recommendation header here."
        assertEquals(text, extractFinalRecommendation(text))
    }

    // ── formatRejectionReason ─────────────────────────────────────────────────

    @Test
    fun `formatRejectionReason replaces Boy with user first name`() {
        val reason = "Boy: Mars in H7"
        val formatted = formatRejectionReason(reason, userName = "Prabhu Kumar", partnerName = "Priya Sharma")
        assertTrue(formatted.contains("Prabhu"))
        assertFalse(formatted.contains("Boy"))
    }

    @Test
    fun `formatRejectionReason replaces Girl with partner first name`() {
        val reason = "Girl: No dosha"
        val formatted = formatRejectionReason(reason, userName = "Prabhu", partnerName = "Priya Sharma")
        assertTrue(formatted.contains("Priya"))
        assertFalse(formatted.contains("Girl"))
    }

    @Test
    fun `formatRejectionReason replaces possessive forms`() {
        val reason = "Boy's Mars aspects Girl's Moon"
        val formatted = formatRejectionReason(reason, userName = "Prabhu", partnerName = "Priya")
        assertTrue(formatted.contains("Prabhu's"))
        assertTrue(formatted.contains("Priya's"))
    }

    // ── emptyHistoryMessage ───────────────────────────────────────────────────

    @Test
    fun `emptyHistoryMessage returns no history message when search is blank`() {
        val msg = emptyHistoryMessage(searchText = "")
        assertTrue(msg.contains("No") || msg.contains("history"), "Expected a 'no history' message, got: $msg")
        assertFalse(msg.contains("results"), "Should not say 'results' when no search term: $msg")
    }

    @Test
    fun `emptyHistoryMessage returns no results message when search is non-blank`() {
        val msg = emptyHistoryMessage(searchText = "Priya")
        assertTrue(msg.contains("results") || msg.contains("No results"), "Expected 'no results' message: $msg")
    }

    // ── activeDoshaTileCount ──────────────────────────────────────────────────

    @Test
    fun `activeDoshaTileCount counts only active doshas`() {
        val active = YogaItem(id = "d1", name = "Nadi", yogaKey = "nadi", status = "A", strengthRaw = 80.0, description = "")
        val inactive = YogaItem(id = "d2", name = "Bhakoot", yogaKey = "bhakoot", status = "C", strengthRaw = 0.0, description = "")
        assertEquals(1, activeDoshaTileCount(listOf(active, inactive)))
    }

    @Test
    fun `activeDoshaTileCount returns 0 when no active doshas`() {
        val inactive = YogaItem(id = "d1", name = "Nadi", yogaKey = "nadi", status = "C", strengthRaw = 0.0, description = "")
        assertEquals(0, activeDoshaTileCount(listOf(inactive)))
    }

    @Test
    fun `activeDoshaTileCount returns 0 for empty list`() {
        assertEquals(0, activeDoshaTileCount(emptyList()))
    }

    // ── kalsarpaSharedRemedies ────────────────────────────────────────────────

    @Test
    fun `kalsarpaSharedRemedies deduplicates and caps at 3`() {
        val boy = listOf("Chant mantra", "Donate food", "Visit temple", "Fasting", "Pooja")
        val girl = listOf("Chant mantra", "Meditate")
        val result = kalsarpaSharedRemedies(boyRemedies = boy, girlRemedies = girl)
        assertTrue(result.size <= 3)
        assertTrue(result.distinct().size == result.size, "Should have no duplicates")
    }

    @Test
    fun `kalsarpaSharedRemedies deduplicates overlapping items`() {
        val boy = listOf("A", "B")
        val girl = listOf("A", "C")
        val result = kalsarpaSharedRemedies(boyRemedies = boy, girlRemedies = girl)
        assertEquals(result.count { it == "A" }, 1, "Duplicate 'A' should appear only once")
    }

    @Test
    fun `kalsarpaSharedRemedies returns all when fewer than 3 unique`() {
        val result = kalsarpaSharedRemedies(boyRemedies = listOf("A"), girlRemedies = listOf("B"))
        assertEquals(2, result.size)
    }

    // ── affirmationWeightOrder includes varna ─────────────────────────────────

    @Test
    fun `affirmationWeightOrder includes all 8 koota keys including varna`() {
        val keys = affirmationWeightOrder()
        assertTrue(keys.contains("varna"), "Expected 'varna' in weight order but got: $keys")
        assertEquals(8, keys.size, "Expected all 8 kootas, got: $keys")
    }

    // ── MangalDoshaModel.baseSeverityLabel ────────────────────────────────────

    @Test
    fun `baseSeverityLabel returns Severe when baseScore is 0_80`() {
        val model = MangalDoshaModel(
            hasMangalDosha = true, severity = "severe", marsHouse = 1,
            exceptions = emptyList(), description = null, baseScore = 0.80,
        )
        assertEquals("Severe", model.baseSeverityLabel)
    }

    @Test
    fun `baseSeverityLabel returns Mild when baseScore is 0_10`() {
        val model = MangalDoshaModel(
            hasMangalDosha = true, severity = "mild", marsHouse = 1,
            exceptions = emptyList(), description = null, baseScore = 0.10,
        )
        assertEquals("Mild", model.baseSeverityLabel)
    }

    @Test
    fun `baseSeverityLabel returns None when baseScore is 0`() {
        val model = MangalDoshaModel(
            hasMangalDosha = false, severity = null, marsHouse = null,
            exceptions = emptyList(), description = null, baseScore = 0.0,
        )
        assertEquals("None", model.baseSeverityLabel)
    }

    // ── MangalDoshaModel.exceptionImpactSummary (proper cancellation/reduction) ─

    @Test
    fun `exceptionImpactSummary returns null when no exceptions`() {
        val model = MangalDoshaModel(
            hasMangalDosha = true, severity = "high", marsHouse = 1,
            exceptions = emptyList(), description = null,
        )
        assertNull(model.exceptionImpactSummary)
    }

    @Test
    fun `exceptionImpactSummary says cancelled when isCancelledByExceptions is true`() {
        val model = MangalDoshaModel(
            hasMangalDosha = false, severity = "none", marsHouse = 1,
            exceptions = listOf("mars_in_capricorn", "jupiter_aspect"),
            description = null, isCancelledByExceptions = true,
        )
        val summary = model.exceptionImpactSummary
        assertNotNull(summary)
        assertTrue(summary!!.contains("cancel", ignoreCase = true), "Expected 'cancel' in: $summary")
        assertTrue(summary.contains("2"), "Expected exception count '2' in: $summary")
    }

    @Test
    fun `exceptionImpactSummary shows reduced severity with before-after when not fully cancelled`() {
        val model = MangalDoshaModel(
            hasMangalDosha = true, severity = "mild", marsHouse = 1,
            exceptions = listOf("venus_conjunct"),
            description = null, isCancelledByExceptions = false, baseScore = 0.80,
        )
        val summary = model.exceptionImpactSummary
        assertNotNull(summary)
        assertTrue(
            summary!!.contains("Severe", ignoreCase = true) || summary.contains("reduced", ignoreCase = true),
            "Expected before-label or 'reduced' in: $summary"
        )
    }

    // ── MangalDoshaModel.activeDoshaSourcesDisplay ────────────────────────────

    @Test
    fun `activeDoshaSourcesDisplay returns null when no dosha_from data`() {
        val model = MangalDoshaModel(
            hasMangalDosha = true, severity = "high", marsHouse = 7,
            exceptions = emptyList(), description = null, doshaFrom = null,
        )
        assertNull(model.activeDoshaSourcesDisplay)
    }

    @Test
    fun `activeDoshaSourcesDisplay includes Lagna position when triggered`() {
        val model = MangalDoshaModel(
            hasMangalDosha = true, severity = "high", marsHouse = 7,
            exceptions = emptyList(), description = null,
            doshaFrom = mapOf(
                "lagna" to true, "mars_house_from_lagna" to 7,
                "moon" to false, "venus" to false,
            ),
        )
        val display = model.activeDoshaSourcesDisplay
        assertNotNull(display)
        assertTrue(display!!.contains("Lagna"), "Expected 'Lagna' in: $display")
        assertTrue(display.contains("7"), "Expected house 7 in: $display")
    }

    @Test
    fun `activeDoshaSourcesDisplay concatenates multiple triggered sources`() {
        val model = MangalDoshaModel(
            hasMangalDosha = true, severity = "high", marsHouse = 7,
            exceptions = emptyList(), description = null,
            doshaFrom = mapOf(
                "lagna" to true, "mars_house_from_lagna" to 7,
                "moon" to true, "mars_house_from_moon" to 2,
                "venus" to false,
            ),
        )
        val display = model.activeDoshaSourcesDisplay
        assertNotNull(display)
        assertTrue(display!!.contains("Lagna"), "Expected 'Lagna' in: $display")
        assertTrue(display.contains("Moon"), "Expected 'Moon' in: $display")
    }

    // ── KutaTextBuilder.richDescription ──────────────────────────────────────

    @Test
    fun `kutaRichDescription for nadi includes partner Nadi values when present`() {
        val kuta = KutaDetail(
            key = "nadi", label = "Nadi", icon = "🌊",
            score = 8.0, maxScore = 8.0,
            boyValue = "Aadi", girlValue = "Madhya",
        )
        val desc = kutaRichDescription(kuta, boyName = "Prabhu", girlName = "Priya")
        assertTrue(desc.contains("Aadi"), "Expected boyValue 'Aadi' in: $desc")
        assertTrue(desc.contains("Madhya"), "Expected girlValue 'Madhya' in: $desc")
    }

    @Test
    fun `kutaRichDescription for nadi with active dosha mentions Nadi Dosha`() {
        val kuta = KutaDetail(
            key = "nadi", label = "Nadi", icon = "🌊",
            score = 0.0, maxScore = 8.0,
            doshaPresent = true, doshaCancelled = false,
            boyValue = "Aadi", girlValue = "Aadi",
        )
        val desc = kutaRichDescription(kuta, boyName = "Prabhu", girlName = "Priya")
        assertTrue(desc.contains("Nadi Dosha", ignoreCase = true), "Expected 'Nadi Dosha' in: $desc")
    }

    @Test
    fun `kutaRichDescription for nadi with cancelled dosha mentions cancellation`() {
        val kuta = KutaDetail(
            key = "nadi", label = "Nadi", icon = "🌊",
            score = 0.0, maxScore = 8.0,
            doshaPresent = true, doshaCancelled = true,
            cancellationReason = "same sign", adjustedScore = 8.0,
        )
        val desc = kutaRichDescription(kuta, boyName = "Prabhu", girlName = "Priya")
        assertTrue(desc.contains("cancel", ignoreCase = true), "Expected 'cancel' in: $desc")
    }

    @Test
    fun `kutaRichDescription for gana includes Gana values`() {
        val kuta = KutaDetail(
            key = "gana", label = "Gana", icon = "✨",
            score = 6.0, maxScore = 6.0,
            boyValue = "Deva", girlValue = "Deva",
        )
        val desc = kutaRichDescription(kuta, boyName = "Prabhu", girlName = "Priya")
        assertTrue(desc.contains("Deva"), "Expected Gana value 'Deva' in: $desc")
    }

    @Test
    fun `kutaRichDescription for bhakoot includes doshaType when present`() {
        val kuta = KutaDetail(
            key = "bhakoot", label = "Bhakoot", icon = "💫",
            score = 0.0, maxScore = 7.0,
            doshaPresent = true, doshaCancelled = false,
            boyValue = "Aries", girlValue = "Libra",
            doshaType = "6/8",
        )
        val desc = kutaRichDescription(kuta, boyName = "Prabhu", girlName = "Priya")
        assertTrue(desc.contains("6/8") || desc.contains("Bhakoot Dosha", ignoreCase = true),
            "Expected doshaType or dosha mention in: $desc")
    }

    @Test
    fun `kutaRichDescription for tara includes taraBoyToGirl counts when present`() {
        val kuta = KutaDetail(
            key = "tara", label = "Tara", icon = "⭐",
            score = 3.0, maxScore = 3.0,
            taraBoyToGirl = 7, taraGirlToBoy = 4,
        )
        val desc = kutaRichDescription(kuta, boyName = "Prabhu", girlName = "Priya")
        assertTrue(desc.contains("7") && desc.contains("4"), "Expected tara counts 7 and 4 in: $desc")
    }

    // ── KutaTextBuilder.classicalPrompt ──────────────────────────────────────

    @Test
    fun `kutaClassicalPrompt includes partner names and score`() {
        val kuta = KutaDetail(
            key = "nadi", label = "Nadi", icon = "🌊",
            score = 8.0, maxScore = 8.0,
        )
        val prompt = kutaClassicalPrompt(kuta, boyName = "Prabhu", girlName = "Priya")
        assertTrue(prompt.contains("Prabhu"), "Expected boyName in: $prompt")
        assertTrue(prompt.contains("Priya"), "Expected girlName in: $prompt")
        assertTrue(prompt.contains("8"), "Expected score in: $prompt")
    }

    @Test
    fun `kutaClassicalPrompt for nadi with active dosha mentions same Nadi`() {
        val kuta = KutaDetail(
            key = "nadi", label = "Nadi", icon = "🌊",
            score = 0.0, maxScore = 8.0,
            doshaPresent = true, doshaCancelled = false,
        )
        val prompt = kutaClassicalPrompt(kuta, boyName = "Prabhu", girlName = "Priya")
        assertTrue(
            prompt.contains("same Nadi", ignoreCase = true) || prompt.contains("Nadi Dosha", ignoreCase = true),
            "Expected dosha mention in: $prompt"
        )
    }

    @Test
    fun `kutaClassicalPrompt for gana with dosha includes dosha context`() {
        val kuta = KutaDetail(
            key = "gana", label = "Gana", icon = "✨",
            score = 0.0, maxScore = 6.0,
            doshaPresent = true, doshaCancelled = false,
            boyValue = "Rakshasa", girlValue = "Deva",
        )
        val prompt = kutaClassicalPrompt(kuta, boyName = "Prabhu", girlName = "Priya")
        assertTrue(
            prompt.contains("Gana", ignoreCase = true) && prompt.contains("dosha", ignoreCase = true),
            "Expected gana dosha context in: $prompt"
        )
    }

    // ── AffirmationBuilder ────────────────────────────────────────────────────

    @Test
    fun `affirmationBuildText score 28+ returns excellent tier`() {
        val kutas = emptyList<KutaDetail>()
        val result = affirmationBuildText(kutas, adjustedScore = null, totalScore = 28)
        assertTrue(result.isNotEmpty(), "Expected non-empty affirmation for score 28")
    }

    @Test
    fun `affirmationBuildText with 3 perfect kootas names all three`() {
        val kutas = listOf(
            KutaDetail(key = "nadi", label = "Nadi", icon = "~", score = 8.0, maxScore = 8.0),
            KutaDetail(key = "bhakoot", label = "Bhakoot", icon = "~", score = 7.0, maxScore = 7.0),
            KutaDetail(key = "gana", label = "Gana", icon = "~", score = 6.0, maxScore = 6.0),
        )
        val result = affirmationBuildText(kutas, adjustedScore = null, totalScore = 30)
        assertTrue(
            result.contains("Nadi", ignoreCase = true) &&
            result.contains("Bhakoot", ignoreCase = true) &&
            result.contains("Gana", ignoreCase = true),
            "Expected all 3 perfect kootas named: $result"
        )
    }

    @Test
    fun `affirmationBuildText uses adjustedScore when present`() {
        val kutas = emptyList<KutaDetail>()
        val result = affirmationBuildText(kutas, adjustedScore = 30, totalScore = 20)
        assertTrue(result.contains("30"), "Expected adjusted score 30 in: $result")
    }

    @Test
    fun `affirmationBuildText with 1 perfect koota mentions single koota`() {
        val kutas = listOf(
            KutaDetail(key = "nadi", label = "Nadi", icon = "~", score = 8.0, maxScore = 8.0),
            KutaDetail(key = "bhakoot", label = "Bhakoot", icon = "~", score = 3.0, maxScore = 7.0),
        )
        val result = affirmationBuildText(kutas, adjustedScore = null, totalScore = 25)
        assertTrue(result.contains("Nadi", ignoreCase = true), "Expected Nadi in single-perfect: $result")
    }

    @Test
    fun `affirmationBuildText with 2 perfect kootas uses both names`() {
        val kutas = listOf(
            KutaDetail(key = "nadi", label = "Nadi", icon = "~", score = 8.0, maxScore = 8.0),
            KutaDetail(key = "bhakoot", label = "Bhakoot", icon = "~", score = 7.0, maxScore = 7.0),
            KutaDetail(key = "gana", label = "Gana", icon = "~", score = 3.0, maxScore = 6.0),
        )
        val result = affirmationBuildText(kutas, adjustedScore = null, totalScore = 26)
        assertTrue(
            result.contains("Nadi", ignoreCase = true) && result.contains("Bhakoot", ignoreCase = true),
            "Expected two perfect kootas named: $result"
        )
    }

    // ── synergyArcColor ───────────────────────────────────────────────────────

    @Test
    fun `synergyArcColor returns green for percentage 0_75 or above`() {
        assertEquals("green", synergyArcColorLabel(0.75))
    }

    @Test
    fun `synergyArcColor returns green for percentage 1_0`() {
        assertEquals("green", synergyArcColorLabel(1.0))
    }

    @Test
    fun `synergyArcColor returns gold for percentage 0_5 to 0_74`() {
        assertEquals("gold", synergyArcColorLabel(0.5))
    }

    @Test
    fun `synergyArcColor returns gold for percentage 0_6`() {
        assertEquals("gold", synergyArcColorLabel(0.6))
    }

    @Test
    fun `synergyArcColor returns red for percentage below 0_5`() {
        assertEquals("red", synergyArcColorLabel(0.49))
    }

    @Test
    fun `synergyArcColor returns red for percentage 0_0`() {
        assertEquals("red", synergyArcColorLabel(0.0))
    }

    // ── glassPillStatusColor ──────────────────────────────────────────────────

    @Test
    fun `glassPillStatusColor returns green when dosha cancelled`() {
        assertEquals("green", glassPillStatusColorLabel(doshaPresent = true, doshaCancelled = true, score = 0.0, maxScore = 8.0, key = "nadi"))
    }

    @Test
    fun `glassPillStatusColor returns red when dosha active uncancelled`() {
        assertEquals("red", glassPillStatusColorLabel(doshaPresent = true, doshaCancelled = false, score = 0.0, maxScore = 8.0, key = "nadi"))
    }

    @Test
    fun `glassPillStatusColor returns green for perfect score ratio`() {
        assertEquals("green", glassPillStatusColorLabel(doshaPresent = false, doshaCancelled = false, score = 8.0, maxScore = 8.0, key = "nadi"))
    }

    @Test
    fun `glassPillStatusColor returns green for ratio 0_8 or above`() {
        assertEquals("green", glassPillStatusColorLabel(doshaPresent = false, doshaCancelled = false, score = 6.4, maxScore = 8.0, key = "gana"))
    }

    @Test
    fun `glassPillStatusColor returns red for ratio below 0_25`() {
        assertEquals("red", glassPillStatusColorLabel(doshaPresent = false, doshaCancelled = false, score = 1.0, maxScore = 8.0, key = "gana"))
    }

    @Test
    fun `glassPillStatusColor returns yellow for ratio between 0_25 and 0_8`() {
        assertEquals("yellow", glassPillStatusColorLabel(doshaPresent = false, doshaCancelled = false, score = 4.0, maxScore = 8.0, key = "gana"))
    }

    @Test
    fun `glassPillStatusColor returns red for nadi at zero even without dosha flag`() {
        assertEquals("red", glassPillStatusColorLabel(doshaPresent = false, doshaCancelled = false, score = 0.0, maxScore = 8.0, key = "nadi"))
    }

    @Test
    fun `glassPillStatusColor returns red for bhakoot at zero even without dosha flag`() {
        assertEquals("red", glassPillStatusColorLabel(doshaPresent = false, doshaCancelled = false, score = 0.0, maxScore = 7.0, key = "bhakoot"))
    }

    // ── orbitAngleDegrees ─────────────────────────────────────────────────────

    @Test
    fun `orbitAngleDegrees first item at -90 degrees`() {
        assertEquals(-90.0, orbitAngleDegrees(index = 0, total = 8), 0.001)
    }

    @Test
    fun `orbitAngleDegrees second item at -45 degrees`() {
        assertEquals(-45.0, orbitAngleDegrees(index = 1, total = 8), 0.001)
    }

    @Test
    fun `orbitAngleDegrees fifth item at 90 degrees`() {
        assertEquals(90.0, orbitAngleDegrees(index = 4, total = 8), 0.001)
    }

    // ── profileSwitcherFirstName ──────────────────────────────────────────────

    @Test
    fun `profileSwitcherFirstName extracts first word`() {
        assertEquals("Prabhu", profileSwitcherFirstName("Prabhu Kushwaha"))
    }

    @Test
    fun `profileSwitcherFirstName returns full name when single word`() {
        assertEquals("Priya", profileSwitcherFirstName("Priya"))
    }

    @Test
    fun `profileSwitcherFirstName handles empty string`() {
        assertEquals("", profileSwitcherFirstName(""))
    }

    // ── expandSignAbbreviation ────────────────────────────────────────────────

    @Test
    fun `expandSignAbbreviation replaces Ar with Aries`() {
        assertEquals("Aries", expandSignAbbreviation("Ar"))
    }

    @Test
    fun `expandSignAbbreviation replaces Cn with Cancer`() {
        assertEquals("Cancer", expandSignAbbreviation("Cn"))
    }

    @Test
    fun `expandSignAbbreviation replaces multiple abbreviations in one string`() {
        val result = expandSignAbbreviation("Ar and Cn")
        assertTrue(result.contains("Aries"), "Expected Aries but got: $result")
        assertTrue(result.contains("Cancer"), "Expected Cancer but got: $result")
    }

    @Test
    fun `expandSignAbbreviation leaves unknown tokens unchanged`() {
        assertEquals("Moon in House 7", expandSignAbbreviation("Moon in House 7"))
    }

    @Test
    fun `expandSignAbbreviation replaces Sc with Scorpio`() {
        assertEquals("Scorpio", expandSignAbbreviation("Sc"))
    }

    // ── circleAvatarInitial ───────────────────────────────────────────────────

    @Test
    fun `circleAvatarInitial returns uppercase first letter`() {
        assertEquals("P", circleAvatarInitial("Prabhu"))
    }

    @Test
    fun `circleAvatarInitial works with full name`() {
        assertEquals("S", circleAvatarInitial("Smita Sharma"))
    }

    @Test
    fun `circleAvatarInitial returns question mark for empty string`() {
        assertEquals("?", circleAvatarInitial(""))
    }

    // ── replaceGenericLabels — **Boy ( and **Girl ( patterns ──────────────────

    @Test
    fun `replaceGenericLabels replaces bold Boy open-paren with boyName`() {
        val result = replaceGenericLabels("**Boy (Lagna: Gemini)", boyName = "Prabhu", girlName = "Priya")
        assertTrue(result.contains("**Prabhu ("), "Expected '**Prabhu (' in: $result")
        assertFalse(result.contains("**Boy ("), "Did not expect '**Boy (' in: $result")
    }

    @Test
    fun `replaceGenericLabels replaces bold Girl open-paren with girlName`() {
        val result = replaceGenericLabels("**Girl (Lagna: Aries)", boyName = "Prabhu", girlName = "Priya")
        assertTrue(result.contains("**Priya ("), "Expected '**Priya (' in: $result")
        assertFalse(result.contains("**Girl ("), "Did not expect '**Girl (' in: $result")
    }

    @Test
    fun `replaceGenericLabels handles both patterns in same string`() {
        val text = "**Boy (H1: Sun)** and **Girl (H7: Venus)**"
        val result = replaceGenericLabels(text, boyName = "Prabhu", girlName = "Priya")
        assertTrue(result.contains("**Prabhu ("), "Expected **Prabhu (: $result")
        assertTrue(result.contains("**Priya ("), "Expected **Priya (: $result")
    }

    // ── localizeExceptionKeysInText ───────────────────────────────────────────

    @Test
    fun `localizeExceptionKeysInText replaces single known key in sentence`() {
        val text = "Exception: same_dosha_match applies here"
        val result = localizeExceptionKeysInText(text)
        assertTrue(result.contains("Same Dosha Match"), "Expected localized key in: $result")
        assertFalse(result.contains("same_dosha_match"), "Should not contain raw key: $result")
    }

    @Test
    fun `localizeExceptionKeysInText replaces multiple known keys`() {
        val text = "Both jupiter_in_1_2_4_7 and mars_in_capricorn cancel this"
        val result = localizeExceptionKeysInText(text)
        assertFalse(result.contains("jupiter_in_1_2_4_7"), "Raw key should be replaced")
        assertFalse(result.contains("mars_in_capricorn"), "Raw key should be replaced")
    }

    @Test
    fun `localizeExceptionKeysInText leaves non-key text unchanged`() {
        val text = "No keys here at all."
        assertEquals(text, localizeExceptionKeysInText(text))
    }

    // ── intensityFactorCountLabel ──────────────────────────────────────────────

    @Test
    fun `intensityFactorCountLabel singular for count 1`() {
        val label = intensityFactorCountLabel(1)
        assertTrue(label.contains("1"), "Expected '1' in: $label")
        assertFalse(label.contains("factors"), "Singular should not say 'factors': $label")
        assertTrue(label.lowercase().contains("factor"), "Expected 'factor' in: $label")
    }

    @Test
    fun `intensityFactorCountLabel plural for count 3`() {
        val label = intensityFactorCountLabel(3)
        assertTrue(label.contains("3"), "Expected '3' in: $label")
        assertTrue(label.contains("factors"), "Expected 'factors' in: $label")
    }

    @Test
    fun `intensityFactorCountLabel zero is plural`() {
        val label = intensityFactorCountLabel(0)
        assertTrue(label.contains("0"), "Expected '0' in: $label")
        assertTrue(label.contains("factors"), "Expected 'factors' for 0: $label")
    }

    // ── kutaCellStatusIcon ────────────────────────────────────────────────────

    @Test
    fun `kutaCellStatusIcon returns checkmark for full score`() {
        val icon = kutaCellStatusIcon(doshaPresent = false, doshaCancelled = false, score = 8.0, maxScore = 8.0)
        assertEquals("✅", icon)
    }

    @Test
    fun `kutaCellStatusIcon returns cross for active uncancelled dosha`() {
        val icon = kutaCellStatusIcon(doshaPresent = true, doshaCancelled = false, score = 0.0, maxScore = 8.0)
        assertEquals("🚫", icon)
    }

    @Test
    fun `kutaCellStatusIcon returns warning for dosha cancelled`() {
        val icon = kutaCellStatusIcon(doshaPresent = true, doshaCancelled = true, score = 0.0, maxScore = 8.0)
        assertEquals("⚠️", icon)
    }

    @Test
    fun `kutaCellStatusIcon returns checkmark for partial score without dosha`() {
        val icon = kutaCellStatusIcon(doshaPresent = false, doshaCancelled = false, score = 5.0, maxScore = 8.0)
        assertEquals("✅", icon)
    }

    // ── compactPartnerCardScoreText ───────────────────────────────────────────

    @Test
    fun `compactPartnerCardScoreText returns plain score when adjusted equals overall`() {
        val text = compactPartnerCardScoreText(adjustedScore = 25, overallScore = 25, maxScore = 36)
        assertEquals("25/36", text)
    }

    @Test
    fun `compactPartnerCardScoreText includes adjusted indicator when scores differ`() {
        val text = compactPartnerCardScoreText(adjustedScore = 28, overallScore = 22, maxScore = 36)
        assertTrue(text.contains("28"), "Expected adjusted score 28 in: $text")
        assertTrue(text.contains("*") || text.contains("adj", ignoreCase = true) || text.contains("22"),
            "Expected raw score or adjustment indicator in: $text")
    }

    // ── rejectionReasonScoreHighlight ─────────────────────────────────────────

    @Test
    fun `rejectionReasonScoreHighlight extracts N slash 36 from reason`() {
        val reason = "Adjusted Ashtakoot score 15/36 — below minimum."
        val pair = rejectionReasonScoreHighlight(reason)
        assertNotNull(pair)
        assertEquals("15/36", pair!!.second)
    }

    @Test
    fun `rejectionReasonScoreHighlight returns text before score as first`() {
        val reason = "Score is 12/36 which is below threshold"
        val pair = rejectionReasonScoreHighlight(reason)
        assertNotNull(pair)
        assertFalse(pair!!.first.contains("12/36"), "First part should not include the score")
    }

    @Test
    fun `rejectionReasonScoreHighlight returns null when no score pattern`() {
        val reason = "Nadi Dosha is active — same biological constitution."
        assertNull(rejectionReasonScoreHighlight(reason))
    }

    // ── allDoshaItems ─────────────────────────────────────────────────────────

    @Test
    fun `allDoshaItems includes both active and cancelled doshas`() {
        val activeDosha = YogaItem(id = "d1", name = "Nadi", yogaKey = "nadi", status = "A", strengthRaw = 80.0, description = "", isDosha = true)
        val cancelledDosha = YogaItem(id = "d2", name = "Bhakoot", yogaKey = "bhakoot", status = "C", strengthRaw = 0.0, description = "", isDosha = true)
        val data = com.destinyai.astrology.domain.model.YogaDoshaData(yogas = emptyList(), doshas = listOf(activeDosha, cancelledDosha))
        val result = allDoshaItems(data)
        assertEquals(2, result.size)
    }

    @Test
    fun `allDoshaItems returns empty list for null data`() {
        assertTrue(allDoshaItems(null).isEmpty())
    }

    @Test
    fun `allDoshaItems returns empty when no doshas`() {
        val data = com.destinyai.astrology.domain.model.YogaDoshaData(yogas = emptyList(), doshas = emptyList())
        assertTrue(allDoshaItems(data).isEmpty())
    }

    // ── shareCardDoshaOverrideText ────────────────────────────────────────────

    @Test
    fun `shareCardDoshaOverrideText returns non-blank when not recommended and scores differ`() {
        val text = shareCardDoshaOverrideText(isRecommended = false, adjustedScore = 28, totalScore = 22)
        assertTrue(text.isNotBlank(), "Expected override text when scores differ and not recommended")
    }

    @Test
    fun `shareCardDoshaOverrideText returns blank when recommended`() {
        val text = shareCardDoshaOverrideText(isRecommended = true, adjustedScore = 28, totalScore = 22)
        assertTrue(text.isBlank(), "No override text when recommended")
    }

    @Test
    fun `shareCardDoshaOverrideText returns blank when scores equal even if not recommended`() {
        val text = shareCardDoshaOverrideText(isRecommended = false, adjustedScore = 22, totalScore = 22)
        assertTrue(text.isBlank(), "No override text when scores equal")
    }

    // ── kutaCellScoreDisplay ──────────────────────────────────────────────────

    @Test
    fun `kutaCellScoreDisplay returns 0 arrow max when cancelled dosha`() {
        val result = kutaCellScoreDisplay(doshaCancelled = true, score = 0.0, maxScore = 8.0)
        assertEquals("0→8", result)
    }

    @Test
    fun `kutaCellScoreDisplay returns score slash max when no cancellation`() {
        val result = kutaCellScoreDisplay(doshaCancelled = false, score = 5.0, maxScore = 8.0)
        assertEquals("5/8", result)
    }

    @Test
    fun `kutaCellScoreDisplay truncates decimals to int`() {
        val result = kutaCellScoreDisplay(doshaCancelled = false, score = 3.0, maxScore = 7.0)
        assertEquals("3/7", result)
    }

    // ── kutaCellStatusIconV2 ──────────────────────────────────────────────────

    @Test
    fun `kutaCellStatusIconV2 returns null for partial score without dosha`() {
        val result = kutaCellStatusIconV2(
            doshaPresent = false, doshaCancelled = false,
            score = 5.0, maxScore = 8.0
        )
        assertNull(result, "Partial score without dosha should return null icon")
    }

    @Test
    fun `kutaCellStatusIconV2 returns checkmark for full score without dosha`() {
        val result = kutaCellStatusIconV2(
            doshaPresent = false, doshaCancelled = false,
            score = 8.0, maxScore = 8.0
        )
        assertEquals("✅", result)
    }

    @Test
    fun `kutaCellStatusIconV2 returns ban for active uncancelled dosha`() {
        val result = kutaCellStatusIconV2(
            doshaPresent = true, doshaCancelled = false,
            score = 0.0, maxScore = 8.0
        )
        assertEquals("🚫", result)
    }

    @Test
    fun `kutaCellStatusIconV2 returns checkmark for cancelled dosha`() {
        val result = kutaCellStatusIconV2(
            doshaPresent = true, doshaCancelled = true,
            score = 0.0, maxScore = 8.0
        )
        assertEquals("✅", result)
    }

    @Test
    fun `kutaCellStatusIconV2 returns warning for zero non-critical score`() {
        val result = kutaCellStatusIconV2(
            doshaPresent = false, doshaCancelled = false,
            score = 0.0, maxScore = 8.0
        )
        assertEquals("⚠️", result)
    }

    // ── cosmicProgressKey ─────────────────────────────────────────────────────

    @Test
    fun `cosmicProgressKey returns first message for index 0`() {
        val messages = listOf("Calculating…", "Aligning planets…", "Reading chart…")
        assertEquals("Calculating…", cosmicProgressKey(0, messages))
    }

    @Test
    fun `cosmicProgressKey wraps around with modulo`() {
        val messages = listOf("A", "B", "C")
        assertEquals("A", cosmicProgressKey(3, messages))
        assertEquals("B", cosmicProgressKey(4, messages))
    }

    @Test
    fun `cosmicProgressKey returns empty string for empty list`() {
        assertEquals("", cosmicProgressKey(0, emptyList()))
    }

    // ── ageBlockBannerVisible ─────────────────────────────────────────────────

    @Test
    fun `ageBlockBannerVisible returns true for non-null non-blank message`() {
        assertTrue(ageBlockBannerVisible("Both partners must be 18+"))
    }

    @Test
    fun `ageBlockBannerVisible returns false for null message`() {
        assertFalse(ageBlockBannerVisible(null))
    }

    @Test
    fun `ageBlockBannerVisible returns false for blank string`() {
        assertFalse(ageBlockBannerVisible(""))
    }

    // ── redirectTargetName ────────────────────────────────────────────────────

    @Test
    fun `redirectTargetName returns boyName when target contains boy name`() {
        assertEquals("Arjun", redirectTargetName(target = "arjun", boyName = "Arjun", girlName = "Priya"))
    }

    @Test
    fun `redirectTargetName returns girlName when target contains girl name`() {
        assertEquals("Priya", redirectTargetName(target = "priya_chart", boyName = "Arjun", girlName = "Priya"))
    }

    @Test
    fun `redirectTargetName returns girlName when target contains girl keyword`() {
        assertEquals("Priya", redirectTargetName(target = "girl", boyName = "Arjun", girlName = "Priya"))
    }

    @Test
    fun `redirectTargetName defaults to boyName when target is null`() {
        assertEquals("Arjun", redirectTargetName(target = null, boyName = "Arjun", girlName = "Priya"))
    }

    @Test
    fun `redirectTargetName defaults to boyName when target does not match either name`() {
        assertEquals("Arjun", redirectTargetName(target = "unknown", boyName = "Arjun", girlName = "Priya"))
    }
}
