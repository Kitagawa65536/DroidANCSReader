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
    onOpenDebug: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionCard(
                title = stringResource(R.string.more_title),
                subtitle = stringResource(R.string.more_description),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.about_version, appVersion),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Button(onClick = onOpenDebug) {
                        Text(stringResource(R.string.debug_info_button))
                    }
                    Button(onClick = onOpenOssLicenses) {
                        Text(stringResource(R.string.oss_licenses_button))
                    }
                }
            }
        }
    }
}
