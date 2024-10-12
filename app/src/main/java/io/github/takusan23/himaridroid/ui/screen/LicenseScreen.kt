package io.github.takusan23.himaridroid.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.takusan23.himaridroid.R

private data class LicenseData(
    val name: String,
    val license: String
)

private val licenseDataList = listOf(
    LicenseData(
        name = "waywardgeek/sonic",
        license = """
   Sonic library
   Copyright 2010, 2011
   Bill Cox
   This file is part of the Sonic Library.

   This file is licensed under the Apache 2.0 license.
   
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
        """.trimIndent()
    ),
    LicenseData(
        name = "google/material-design-icons",
        license = """
   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
        """.trimIndent()
    ),
    LicenseData(
        name = "Kotlin/kotlinx.coroutines",
        license = """
   Copyright 2000-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
        """.trimIndent()
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.license_screen)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painter = painterResource(id = R.drawable.arrow_back_24px), contentDescription = null)
                    }
                }
            )
        },
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.padding(paddingValues)) {
            itemsIndexed(licenseDataList) { index, licenseData ->
                if (index != 0) {
                    HorizontalDivider()
                }
                LicenseItem(
                    modifier = Modifier.fillMaxWidth(),
                    licenseData = licenseData
                )
            }
        }
    }
}

@Composable
private fun LicenseItem(
    modifier: Modifier = Modifier,
    licenseData: LicenseData
) {
    Column(modifier = modifier.padding(10.dp)) {
        Text(text = licenseData.name, fontSize = 18.sp)
        Text(text = licenseData.license)
    }
}