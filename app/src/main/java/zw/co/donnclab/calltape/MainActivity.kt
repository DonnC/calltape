package zw.co.donnclab.calltape

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import zw.co.donnclab.calltape.ui.DialerScreen
import zw.co.donnclab.calltape.ui.HistoryScreen
import zw.co.donnclab.calltape.ui.theme.CallTapeTheme
import zw.co.donnclab.calltape.viewmodel.CallViewModel
import kotlinx.serialization.Serializable

@Serializable
sealed interface NavKey

@Serializable
data object HistoryKey : NavKey

@Serializable
data object DialerKey : NavKey

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CallTapeTheme {
                val backStack = remember { mutableStateListOf<NavKey>(HistoryKey) }
                val callViewModel: CallViewModel = viewModel()

                NavDisplay(
                    backStack = backStack,
                    onBack = { 
                        if (backStack.size > 1) {
                            backStack.removeAt(backStack.size - 1)
                        } else {
                            finish()
                        }
                    },
                    entryProvider = { key ->
                        when (key) {
                            is HistoryKey -> NavEntry(key) {
                                HistoryScreen(
                                    viewModel = callViewModel,
                                    onNavigateToDialer = { backStack.add(DialerKey) }
                                )
                            }
                            is DialerKey -> NavEntry(key) {
                                DialerScreen(
                                    onBack = { backStack.removeAt(backStack.size - 1) }
                                )
                            }
                            else -> error("Unknown key $key")
                        }
                    }
                )
            }
        }
    }
}
