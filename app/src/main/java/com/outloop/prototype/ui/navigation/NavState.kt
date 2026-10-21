package com.outloop.prototype.ui.navigation

/**
 * Navigation state for the onboarding walkthrough.
 */
sealed interface OnboardingStep {
    data object Welcome : OnboardingStep
    data object HowItWorks : OnboardingStep
    data object PrivacyExplanation : OnboardingStep
    data object AccessibilitySetup : OnboardingStep
}

/**
 * Main application navigation destinations.
 */
sealed interface AppScreen {
    data object Dashboard : AppScreen
    data object LiveResults : AppScreen
    data object AccessibilitySetup : AppScreen
}
