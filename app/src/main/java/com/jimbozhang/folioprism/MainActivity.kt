package com.jimbozhang.folioprism

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.jimbozhang.folioprism.platform.storage.StoragePaths
import com.jimbozhang.folioprism.ui.FolioPrismApp
import com.jimbozhang.folioprism.ui.PermissionScreen
import com.jimbozhang.folioprism.ui.theme.FolioPrismTheme

class MainActivity : ComponentActivity() {

    private val storageGranted = mutableStateOf(false)

    private val legacyPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        storageGranted.value = StoragePaths.hasStorageAccess(this)
        if (storageGranted.value) StoragePaths.ensureDirs()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storageGranted.value = StoragePaths.hasStorageAccess(this)
        if (storageGranted.value) StoragePaths.ensureDirs()

        setContent {
            FolioPrismTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (storageGranted.value) {
                        FolioPrismApp()
                    } else {
                        PermissionScreen(
                            onRequestAccess = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    StoragePaths.openStorageSettings(this)
                                } else {
                                    legacyPermissionLauncher.launch(
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        storageGranted.value = StoragePaths.hasStorageAccess(this)
        if (storageGranted.value) StoragePaths.ensureDirs()
    }
}
