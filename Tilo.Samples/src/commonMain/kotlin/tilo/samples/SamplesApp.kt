@file:OptIn(ExperimentalMaterial3Api::class)

package tilo.samples

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

@Composable
fun SamplesApp() {
    var selectedSample by remember { mutableStateOf(Sample.OpenStreetMap) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    TiloSamplesTheme {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                SamplesDrawer(
                    selectedSample = selectedSample,
                    onSelect = { sample ->
                        selectedSample = sample
                        scope.launch { drawerState.close() }
                    },
                )
            },
        ) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                SamplesTopBar(onMenuClick = { scope.launch { drawerState.open() } })
                key(selectedSample) {
                    SampleMap(sample = selectedSample, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
