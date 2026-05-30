package com.destinyai.astrology.ui.compatibility

import com.destinyai.astrology.domain.model.KutaDetail

// KutaTextBuilder — pure functions for kuta rich descriptions and classical prompts.
// Mirrors iOS KutaTextBuilder.swift. All logic is stateless — testable without Compose.

// Expands zodiac sign abbreviations (e.g. "Ar" → "Aries", "Cn" → "Cancer") using word boundaries.
// Mirrors iOS KutaTextBuilder.expand() which uses NSRegularExpression for localized sign names.
internal fun expandSignAbbreviation(value: String): String {
    val map = mapOf(
        "Ar" to "Aries", "Ta" to "Taurus", "Ge" to "Gemini", "Cn" to "Cancer",
        "Le" to "Leo", "Vi" to "Virgo", "Li" to "Libra", "Sc" to "Scorpio",
        "Sg" to "Sagittarius", "Cp" to "Capricorn", "Aq" to "Aquarius", "Pi" to "Pisces",
    )
    var result = value
    for ((abbr, full) in map) {
        result = result.replace(Regex("\\b$abbr\\b"), full)
    }
    return result
}

internal fun kutaRichDescription(kuta: KutaDetail, boyName: String, girlName: String): String {
    val score = kuta.score.toInt()
    val maxScore = kuta.maxScore.toInt()
    val effectiveScore = if (kuta.doshaCancelled && kuta.adjustedScore != null) kuta.adjustedScore.toInt() else score
    val effectiveDisplay = "$effectiveScore out of $maxScore"
    val bv = expandSignAbbreviation(kuta.boyValue ?: "")
    val gv = expandSignAbbreviation(kuta.girlValue ?: "")
    val hasValues = bv.isNotEmpty() && gv.isNotEmpty()
    val cancellation = expandSignAbbreviation(kuta.cancellationReason ?: "exemption conditions in your charts")
    val restoredNote = if (kuta.doshaCancelled) "Your score is restored to $maxScore/$maxScore. This does not count against your match." else ""

    val parts = mutableListOf<String>()

    when (kuta.key.lowercase()) {
        "varna" -> {
            parts += "$boyName's ${kuta.label} compatibility is measured by Varna Koota, which looks at each partner's spiritual and social orientation: Brahmin (wisdom), Kshatriya (leadership), Vaishya (commerce), or Shudra (service/craft)."
            if (hasValues) parts += "$boyName belongs to the $bv Varna and $girlName to the $gv Varna."
            val body = kuta.plainEnglishSummary ?: if (effectiveScore == maxScore)
                "Compatible Varnas indicate natural alignment in how you approach your duties, ambitions, and life purpose."
            else
                "A mismatch here suggests your fundamental life orientations pull in different directions, which can create friction in shared decisions and long-term goals."
            parts += "Their score is $effectiveDisplay. $body"
            when {
                kuta.doshaPresent && !kuta.doshaCancelled -> parts += "Active Varna Dosha. No cancellation was found in your charts."
                kuta.doshaPresent && kuta.doshaCancelled -> parts += "The Varna Dosha is cancelled. $cancellation qualifies for an exemption. $restoredNote"
            }
        }
        "vashya" -> {
            parts += "$boyName's ${kuta.label} compatibility is measured by Vashya Koota, which looks at the natural power dynamic and magnetism between partners, based on each moon sign's symbolic animal group."
            if (hasValues) parts += "$boyName is $bv and $girlName is $gv."
            val body = kuta.plainEnglishSummary?.takeIf { it.isNotEmpty() }
                ?: when {
                    effectiveScore == 2 -> "Mutual Vashya indicates strong natural attraction and a balanced sense of influence between you."
                    effectiveScore == 1 -> "Partial Vashya: there is a draw between you, but it may feel one-sided at times."
                    else -> "No Vashya match. The relationship requires conscious effort to maintain attraction."
                }
            parts += "Their score is $effectiveDisplay. $body"
        }
        "tara" -> {
            parts += "$boyName's ${kuta.label} compatibility is measured by Tara Koota, which counts the distance between each partner's birth Nakshatra. Even-numbered counts are auspicious; odd are inauspicious."
            if (kuta.taraBoyToGirl != null && kuta.taraGirlToBoy != null)
                parts += "$boyName's birth star counts ${kuta.taraBoyToGirl} positions to $girlName's, and the reverse counts ${kuta.taraGirlToBoy}."
            val body = kuta.plainEnglishSummary?.takeIf { it.isNotEmpty() }
                ?: when {
                    effectiveScore == 3 -> "Your life paths are in harmony and you are likely to bring good fortune to each other."
                    effectiveScore >= 1 -> "Mostly harmonious destinies with some areas of misalignment."
                    else -> "Your birth stars indicate life paths that may work against each other's vitality."
                }
            parts += "Their score is $effectiveDisplay. $body"
        }
        "yoni" -> {
            parts += "$boyName's ${kuta.label} compatibility is measured by Yoni Koota, which assigns each Nakshatra a symbolic animal. Matching or friendly animals score highly; hostile pairs score low."
            if (hasValues) parts += "$boyName is $bv and $girlName is $gv."
            val body = (if (!kuta.doshaCancelled) kuta.plainEnglishSummary?.takeIf { it.isNotEmpty() } else null)
                ?: when {
                    effectiveScore == 4 -> "An ideal Yoni match with deep physical chemistry and strong intimate compatibility."
                    effectiveScore >= 2 -> "These two natures are partially compatible with decent physical chemistry."
                    else -> "A hostile Yoni pairing. Physical incompatibility is likely to be a recurring source of friction."
                }
            parts += "Their score is $effectiveDisplay. $body"
            when {
                kuta.doshaPresent && !kuta.doshaCancelled -> parts += "Active Yoni Dosha. The animal pairing here is considered hostile in classical texts. No cancellation was found."
                kuta.doshaPresent && kuta.doshaCancelled -> parts += "The Yoni Dosha is cancelled. $cancellation qualifies for an exemption. $restoredNote"
            }
        }
        "maitri" -> {
            parts += "$boyName's ${kuta.label} compatibility is measured by Maitri Koota, which compares the ruling planets of each partner's moon sign. Friendly lords score 5; neutral 3; enemies 0."
            if (hasValues) parts += "$boyName's moon sign lord is $bv and $girlName's is $gv."
            val body = kuta.plainEnglishSummary?.takeIf { it.isNotEmpty() }
                ?: when {
                    effectiveScore >= 4 -> "These planetary lords are friendly. Natural intellectual rapport and genuine friendship form the core of this relationship."
                    effectiveScore >= 2 -> "Neutral lords. The friendship is functional and stable but may lack deep natural intellectual affinity."
                    else -> "Enemy lords. Intellectual friction and a lack of mutual understanding are likely."
                }
            parts += "Their score is $effectiveDisplay. $body"
        }
        "gana" -> {
            parts += "$boyName's ${kuta.label} compatibility is measured by Gana Koota, which classifies each partner's fundamental nature as divine (Deva), human (Manushya), or intense (Rakshasa)."
            if (hasValues) parts += "$boyName is $bv and $girlName is $gv."
            val body = kuta.plainEnglishSummary?.takeIf { it.isNotEmpty() }
                ?: when {
                    effectiveScore == 6 -> "Matching Ganas. Your temperaments are in natural harmony."
                    effectiveScore == 5 -> "Very close Ganas. Minor differences in approach but fundamentally compatible temperaments."
                    else -> "Mixed Ganas. Real differences in how you each handle conflict, pressure, and emotional expression."
                }
            parts += "Their score is $effectiveDisplay. $body"
            when {
                kuta.doshaPresent && !kuta.doshaCancelled -> {
                    val bvLabel = if (bv.isNotEmpty()) bv else boyName
                    val gvLabel = if (gv.isNotEmpty()) gv else girlName
                    parts += "Active Gana Dosha. When $bvLabel and $gvLabel natures pair up, the dominant nature tends to overpower the gentler one. No cancellation was found in your charts."
                }
                kuta.doshaPresent && kuta.doshaCancelled -> parts += "The Gana Dosha is cancelled. $cancellation qualifies for an exemption. $restoredNote"
            }
        }
        "bhakoot" -> {
            parts += "$boyName's ${kuta.label} compatibility is measured by Bhakoot Koota, the most emotionally significant of all 8 Kootas at 7 points. It governs romantic love, emotional bonding, financial prosperity, and prospects for children."
            if (hasValues) parts += "$boyName's moon is in $bv and $girlName's in $gv."
            val body = kuta.plainEnglishSummary?.takeIf { it.isNotEmpty() }
                ?: when {
                    effectiveScore == 7 -> "Excellent Bhakoot. A strong emotional bond, aligned financial energies, and favourable prospects for family life."
                    !kuta.doshaPresent -> "This moon-sign pairing scores $effectiveScore but does not form a classical Bhakoot Dosha."
                    else -> ""
                }
            parts += if (body.isEmpty()) "Their raw score is $effectiveDisplay." else "Their score is $effectiveDisplay. $body"
            when {
                kuta.doshaPresent && !kuta.doshaCancelled -> {
                    val typeNote = kuta.doshaType?.let { " ($it)" } ?: ""
                    parts += "Active Bhakoot Dosha$typeNote. Classical texts link this pairing with emotional distance and financial hardship. No cancellation was found. This is a significant flag."
                }
                kuta.doshaPresent && kuta.doshaCancelled -> parts += "The Bhakoot Dosha is cancelled. $cancellation qualifies for an exemption. Score restored to $maxScore/$maxScore."
            }
        }
        "nadi" -> {
            parts += "$boyName's ${kuta.label} compatibility is measured by Nadi Koota, the most heavily weighted Koota at 8 points and classically considered the most critical. It looks at the Ayurvedic body-type (Nadi): Aadi (Vata), Madhya (Pitta), or Antya (Kapha)."
            if (hasValues) parts += "$boyName is $bv Nadi and $girlName is $gv Nadi."
            val body = (if (!kuta.doshaCancelled) kuta.plainEnglishSummary?.takeIf { it.isNotEmpty() } else null)
                ?: when {
                    effectiveScore == 8 -> "Different Nadis. Ideal for genetic harmony, health compatibility, and prospects for children."
                    !kuta.doshaPresent -> "Partial Nadi compatibility. Mostly harmonious constitutional energies."
                    else -> ""
                }
            parts += if (body.isEmpty()) "Their score is $effectiveDisplay." else "Their score is $effectiveDisplay. $body"
            when {
                kuta.doshaPresent && !kuta.doshaCancelled -> parts += "Active Nadi Dosha. Identical Nadis form the most serious dosha in Ashtakoot matching. Classical texts associate same-Nadi couples with health challenges and genetic incompatibility. No cancellation found. Classical remedies are strongly recommended."
                kuta.doshaPresent && kuta.doshaCancelled -> parts += "The Nadi Dosha is cancelled. $cancellation qualifies for an exemption. $restoredNote The genetic and health concerns are considered neutralised."
            }
        }
        else -> {
            parts += "${kuta.label} compatibility is measured by ${kuta.key.replaceFirstChar { it.uppercase() }} Koota."
            if (hasValues) parts += "$boyName is $bv and $girlName is $gv."
            kuta.plainEnglishSummary?.takeIf { it.isNotEmpty() }?.let { parts += it }
            parts += "Their score is $effectiveDisplay."
            when {
                kuta.doshaPresent && !kuta.doshaCancelled -> parts += "Active ${kuta.key.replaceFirstChar { it.uppercase() }} Dosha. No cancellation was found in your charts."
                kuta.doshaPresent && kuta.doshaCancelled -> parts += "The dosha is cancelled. $cancellation qualifies for an exemption. $restoredNote"
            }
        }
    }

    return parts.joinToString("\n\n")
}

internal fun kutaClassicalPrompt(kuta: KutaDetail, boyName: String, girlName: String): String {
    val score = kuta.score.toInt()
    val maxScore = kuta.maxScore.toInt()
    val bv = expandSignAbbreviation(kuta.boyValue ?: "")
    val gv = expandSignAbbreviation(kuta.girlValue ?: "")
    val hasValues = bv.isNotEmpty() && gv.isNotEmpty()
    val valuesLine = if (hasValues) "$boyName is $bv and $girlName is $gv. " else ""
    val kutaName = when (kuta.key.lowercase()) {
        "maitri" -> "Maitri"; "bhakoot" -> "Bhakoot"; "nadi" -> "Nadi"
        "gana" -> "Gana"; "vashya" -> "Vashya"; "tara" -> "Tara"
        "yoni" -> "Yoni"; "varna" -> "Varna"
        else -> kuta.key.replaceFirstChar { it.uppercase() }
    }
    val cancellation = expandSignAbbreviation(kuta.cancellationReason ?: "exemption conditions")
    val opening = "$boyName and $girlName scored $score/$maxScore on $kutaName Koota — the Vedic measure of ${kuta.label.lowercase()} compatibility. $valuesLine"

    return when (kuta.key.lowercase()) {
        "varna" -> opening + "Can you explain what Varna Koota classically measures, what these two Varnas mean individually, why this pairing scores the way it does, and what it means practically for how this couple will align in their daily work, life purpose, and shared values?"
        "vashya" -> opening + "Can you explain what Vashya Koota classically measures, what these two types mean, why this pairing scores the way it does, and what it means practically for the power dynamic, magnetism, and sense of influence in this couple's relationship?"
        "tara" -> {
            val taraLine = if (kuta.taraBoyToGirl != null && kuta.taraGirlToBoy != null)
                "$boyName's birth star counts ${kuta.taraBoyToGirl} positions to $girlName's; the reverse counts ${kuta.taraGirlToBoy}. "
            else ""
            "$boyName and $girlName scored $score/$maxScore on Tara Koota — the Vedic measure of destiny and fortune compatibility. $taraLine" +
                "Can you explain what Tara Koota classically measures, how the birth-star counting method works, what these numbers indicate, and what it means in practice — will these two bring good fortune to each other, or is there an astrological drain on vitality and luck?"
        }
        "yoni" -> opening + "Can you explain what Yoni Koota classically measures, what these two animal symbols mean, whether they are friendly, neutral, or hostile to each other, and what it means practically for the physical chemistry and intimacy in this couple's relationship?"
        "maitri" -> opening + "Can you explain what Graha Maitri Koota classically measures, what the classical relationship is between these two planetary lords (friendly, neutral, or enemy), and what this means practically for the intellectual bond, mutual respect, and friendship that underlies this couple's relationship?"
        "gana" -> when {
            kuta.doshaPresent && !kuta.doshaCancelled -> opening + "This mismatch has triggered an active Gana Dosha with no cancellation found. Can you explain what Gana Koota classically measures, what each Gana type means (Deva, Manushya, Rakshasa), why this pairing forms a dosha, what classical texts say about how this shows up in daily life, and what remedies the classical tradition recommends?"
            kuta.doshaPresent && kuta.doshaCancelled -> opening + "A Gana Dosha was identified but cancelled because: $cancellation. Can you explain what the original dosha meant classically, why this cancellation condition applies, whether a cancelled Gana Dosha carries any residual effect on temperament, and what this couple should be aware of?"
            else -> opening + "Can you explain what Gana Koota classically measures, what these Gana types mean individually, and what this score means for day-to-day temperament compatibility — how they handle conflict, stress, and emotional expression together?"
        }
        "bhakoot" -> when {
            kuta.doshaPresent && !kuta.doshaCancelled -> {
                val typeNote = kuta.doshaType?.let { " ($it)" } ?: ""
                opening + "This moon-sign pairing forms an active Bhakoot Dosha$typeNote with no cancellation found. Can you explain what Bhakoot Koota classically measures, why this moon-sign pairing forms a dosha, what classical texts say the consequences are for love, finances, and children, how seriously astrologers weigh this, and what remedies are traditionally prescribed?"
            }
            kuta.doshaPresent && kuta.doshaCancelled -> opening + "A Bhakoot Dosha was identified but cancelled because: $cancellation. Score restored to $maxScore/$maxScore. Can you explain what the original Bhakoot Dosha meant classically, why this cancellation condition applies, whether any residual effect remains, and what this couple should be aware of?"
            else -> opening + "Can you explain what Bhakoot Koota classically measures, what this moon-sign pairing traditionally indicates for emotional connection, financial alignment, and family prospects — and what this score means for this couple's long-term relationship?"
        }
        "nadi" -> when {
            kuta.doshaPresent && !kuta.doshaCancelled -> opening + "Both partners share the same Nadi, forming an active Nadi Dosha with no cancellation found. Can you explain what Nadi Koota classically measures, what the three Nadis (Aadi, Madhya, Antya) represent in Ayurveda and Vedic astrology, why same-Nadi pairing is considered the most serious dosha in Ashtakoot, what classical texts say about the consequences for health and children, and what remedies are traditionally prescribed?"
            kuta.doshaPresent && kuta.doshaCancelled -> opening + "A Nadi Dosha was identified but cancelled because: $cancellation. Can you explain what the original Nadi Dosha meant classically, why it is considered the most serious dosha in Ashtakoot, why this cancellation applies, and whether any residual health or progeny concern remains?"
            else -> opening + "Can you explain what Nadi Koota classically measures, what each Nadi type (Aadi/Vata, Madhya/Pitta, Antya/Kapha) means in Ayurveda, why different Nadis are ideal for compatibility, and what this score means for health harmony and prospects for children?"
        }
        else -> opening + "Can you give a classical Vedic analysis of this $kutaName Koota result, explaining what it measures, what this score means for this couple, and any classical significance to note?"
    }
}
