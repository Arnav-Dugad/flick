package io.github.arnavdugad.flick.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import io.github.arnavdugad.flick.ui.screens.profiles.ProfileEditorScreen
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.arnavdugad.flick.ui.screens.home.HomeScreen
import io.github.arnavdugad.flick.ui.screens.lab.ActionLabScreen
import io.github.arnavdugad.flick.ui.screens.onboarding.OnboardingScreen
import io.github.arnavdugad.flick.ui.screens.settings.SettingsScreen
import io.github.arnavdugad.flick.ui.screens.triggers.TriggerDetailScreen
import io.github.arnavdugad.flick.ui.screens.triggers.TriggersScreen
import io.github.arnavdugad.flick.core.model.TriggerType
import io.github.arnavdugad.flick.ui.theme.FlickMotion
import kotlinx.serialization.Serializable

@Serializable
data object OnboardingKey : NavKey

@Serializable
data object HomeKey : NavKey

@Serializable
data object ActionLabKey : NavKey

@Serializable
data object SettingsKey : NavKey

@Serializable
data object TriggersKey : NavKey

@Serializable
data class TriggerDetailKey(val type: TriggerType, val profileId: String? = null) : NavKey

@Serializable
data class ProfileKey(val id: String) : NavKey

@Composable
fun FlickNavigation(startWithOnboarding: Boolean) {
    val backStack = rememberNavBackStack(if (startWithOnboarding) OnboardingKey else HomeKey)

    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            FlickNavDisplay(backStack)
        }
    }
}

@Composable
private fun FlickNavDisplay(backStack: NavBackStack<NavKey>) {
    NavDisplay(
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        transitionSpec = {
            (slideInHorizontally(FlickMotion.screenSlide) { it / 4 } + fadeIn(FlickMotion.effects()))
                .togetherWith(slideOutHorizontally(FlickMotion.screenSlide) { -it / 10 } + fadeOut(FlickMotion.effects()))
        },
        popTransitionSpec = {
            (slideInHorizontally(FlickMotion.screenSlide) { -it / 10 } + fadeIn(FlickMotion.effects()))
                .togetherWith(slideOutHorizontally(FlickMotion.screenSlide) { it / 4 } + fadeOut(FlickMotion.effects()))
        },
        predictivePopTransitionSpec = {
            (slideInHorizontally(FlickMotion.screenSlide) { -it / 10 } + fadeIn(FlickMotion.effects()))
                .togetherWith(slideOutHorizontally(FlickMotion.screenSlide) { it / 4 } + fadeOut(FlickMotion.effects()))
        },
        entryProvider = entryProvider {
            entry<OnboardingKey> {
                OnboardingScreen(
                    onFinished = {
                        backStack.clear()
                        backStack.add(HomeKey)
                    },
                )
            }
            entry<HomeKey> {
                HomeScreen(
                    onOpenLab = { backStack.add(ActionLabKey) },
                    onOpenSettings = { backStack.add(SettingsKey) },
                    onOpenLibrary = { backStack.add(TriggersKey) },
                    onOpenProfile = { backStack.add(ProfileKey(it)) },
                )
            }
            entry<TriggersKey> {
                TriggersScreen(
                    onBack = { backStack.removeAt(backStack.lastIndex) },
                    onOpenTrigger = { backStack.add(TriggerDetailKey(it)) },
                )
            }
            entry<TriggerDetailKey> { key ->
                TriggerDetailScreen(
                    type = key.type,
                    profileId = key.profileId,
                    onBack = { backStack.removeAt(backStack.lastIndex) },
                )
            }
            entry<ProfileKey> { key ->
                ProfileEditorScreen(
                    profileId = key.id,
                    onBack = { if (backStack.lastOrNull() == key) backStack.removeAt(backStack.lastIndex) },
                    onOpenTrigger = { t, p -> backStack.add(TriggerDetailKey(t, p)) },
                    onOpenProfile = {
                        backStack.removeAt(backStack.lastIndex)
                        backStack.add(ProfileKey(it))
                    },
                )
            }
            entry<ActionLabKey> {
                ActionLabScreen(onBack = { backStack.removeAt(backStack.lastIndex) })
            }
            entry<SettingsKey> {
                SettingsScreen(
                    onBack = { backStack.removeAt(backStack.lastIndex) },
                    onReplayOnboarding = { backStack.add(OnboardingKey) },
                )
            }
        },
    )
}
