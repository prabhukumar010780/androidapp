package com.destinyai.astrology.ui.onboarding

data class OnboardingSlide(
    val titleKey: String,
    val subtitleKey: String? = null,
    val descriptionKey: String = "",
    val iconName: String,
    val showStats: Boolean = false,
    val isFeatureSlide: Boolean = false,
) {
    companion object {
        val slides: List<OnboardingSlide> = listOf(
            OnboardingSlide(
                iconName = "chatgpt",
                titleKey = "onboarding_slide1_title",
                subtitleKey = "onboarding_slide1_subtitle",
                showStats = true,
            ),
            OnboardingSlide(
                iconName = "onboarding_clarity",
                titleKey = "onboarding_slide2_title",
                descriptionKey = "onboarding_slide2_description",
            ),
            OnboardingSlide(
                iconName = "onboarding_personalization",
                titleKey = "onboarding_slide3_title",
                descriptionKey = "onboarding_slide3_description",
            ),
            OnboardingSlide(
                iconName = "onboarding_features",
                titleKey = "onboarding_slide4_title",
                isFeatureSlide = true,
            ),
        )
    }
}
