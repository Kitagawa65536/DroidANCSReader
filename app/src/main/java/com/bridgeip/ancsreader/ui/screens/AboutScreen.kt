package com.bridgeip.ancsreader.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bridgeip.ancsreader.R
import com.bridgeip.ancsreader.ui.components.SectionCard

@Composable
fun AboutScreen(
    appVersion: String,
    onOpenOssLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionCard(
                title = stringResource(R.string.app_name),
                subtitle = stringResource(R.string.about_description),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.about_version, appVersion),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Button(onClick = onOpenOssLicenses) {
                        Text(stringResource(R.string.oss_licenses_button))
                    }
                }
            }
        }
    }
}
