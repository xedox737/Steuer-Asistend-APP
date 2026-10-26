package com.example.ui

import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.api.DriveEnvironment

@Composable
internal fun BuildVariantDriveNotice() {
    if (LocalContext.current.packageName == "com.aistudio.steuerassistent.vnhqkp.phase11a" && DriveEnvironment.isIsolatedTest) {
        Text("Test-Datenspeicher: ${DriveEnvironment.rootFolderName} / _BelegApp-Daten",
            style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("phase11a_drive_notice"))
    }
}
