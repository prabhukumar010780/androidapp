package com.destinyai.astrology.ui.onboarding

import androidx.annotation.DrawableRes
import com.destinyai.astrology.R

data class OnboardingSlide(
    val titleRes: Int,
    val subtitleRes: Int? = null,
    val descriptionRes: Int? = null,
    @DrawableRes val imageRes: Int? = null,
    val showStats: Boolean = false,
    val isFeatureSlide: Boolean = false,
) {
    companion object {
        val slides: List<OnboardingSlide> = listOf(
            OnboardingSlide(
                titleRes = R.string.onboarding_slide1_title,
                subtitleRes = R.string.onboarding_slide1_subtitle,
                imageRes = R.drawable.chat_gpt_logo,
                showStats = true,
            ),
            OnboardingSlide(
                titleRes = R.string.onboarding_slide2_title,
                descriptionRes = R.string.onboarding_slide2_description,
                imageRes = R.drawable.onboarding_clarity,
            ),
            OnboardingSlide(
                titleRes = R.string.onboarding_slide3_title,
                descriptionRes = R.string.onboarding_slide3_description,
                imageRes = R.drawable.onboarding_personalization,
            ),
            OnboardingSlide(
                titleRes = R.string.onboarding_slide4_title,
                imageRes = R.drawable.onboarding_features,
                isFeatureSlide = true,
            ),
        )
    }
}
