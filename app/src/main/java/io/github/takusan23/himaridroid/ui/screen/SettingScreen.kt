package io.github.takusan23.himaridroid.ui.screen

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import io.github.takusan23.himaridroid.R

private const val GITHUB_REPOSITORY_URL = "https://github.com/takusan23/HimariDroid"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen(
    onNavigate: (NavigationPaths) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.setting_screen)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painter = painterResource(id = R.drawable.arrow_back_24px), contentDescription = null)
                    }
                }
            )
        },
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.padding(paddingValues)) {
            item {
                SettingItem(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(id = R.string.setting_open_github_title),
                    description = stringResource(id = R.string.setting_open_github_description),
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, GITHUB_REPOSITORY_URL.toUri())
                        context.startActivity(intent)
                    }
                )
            }
            item {
                SettingItem(
                    modifier = Modifier.fillMaxWidth(),
                    title = stringResource(id = R.string.setting_open_license_title),
                    description = stringResource(id = R.string.setting_open_license_description),
                    onClick = { onNavigate(NavigationPaths.License) }
                )
            }
        }
    }
}

@Composable
private fun SettingItem(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(vertical = 10.dp, horizontal = 20.dp)) {
            Text(text = title, fontSize = 18.sp)
            Text(text = description)
        }
    }
}