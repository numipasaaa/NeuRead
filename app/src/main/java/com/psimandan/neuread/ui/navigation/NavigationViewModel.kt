package com.psimandan.neuread.ui.navigation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.psimandan.neuread.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NavigationViewModel @Inject constructor() : ViewModel() {
    private val _navigationEvents = MutableSharedFlow<NavigationCommand>()
    private val navigationEvents: SharedFlow<NavigationCommand> = _navigationEvents.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            _navigationEvents.resetReplayCache()
        }
    }


    fun resetAndNavigateTo(screen: Screen) {
        viewModelScope.launch {
            _navigationEvents.emit(NavigationCommand.ResetAndNavigate(screen))
        }
    }

    fun navigateTo(screen: Screen) {
        viewModelScope.launch {
            _navigationEvents.emit(NavigationCommand.Navigate(screen))
        }
    }

    fun popBack() {
        viewModelScope.launch {
            _navigationEvents.emit(NavigationCommand.Back)
        }
    }

    fun onNavigationEvents(navController: NavController) {
        viewModelScope.launch {
            navigationEvents.collect { command ->
                if (navController.currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(
                        Lifecycle.State.CREATED) == true) {
                    when (command) {
                        is NavigationCommand.Navigate -> navController.navigate(command.screen.route)
                        is NavigationCommand.Back -> navController.popBackStack()
                        is NavigationCommand.ResetAndNavigate -> {
                            navController.navigate(command.screen.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    inclusive = true
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

sealed class NavigationCommand {
    data class Navigate(val screen: Screen) : NavigationCommand()
    data object Back : NavigationCommand()
    data class ResetAndNavigate(val screen: Screen) : NavigationCommand()
}
