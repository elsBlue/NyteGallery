package com.nytefall.nytegallery

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nytefall.nytegallery.data.OnboardingPrefs
import com.nytefall.nytegallery.ui.GalleryScreen
import com.nytefall.nytegallery.ui.GalleryViewModel
import com.nytefall.nytegallery.ui.LocalHostActivity
import com.nytefall.nytegallery.ui.SCREENSHOTS_ALLOWED
import com.nytefall.nytegallery.ui.theme.SAOGalleryTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Splash stays only while the first frame is prepared; no extra delay.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Decide before the gallery loads (loading seeds the vault, which marks the install as existing).
        val onboardingPrefs = OnboardingPrefs(this)
        val showOnboarding = onboardingPrefs.shouldShow()
        // Set before any content is drawn, so screenshots and the Recent apps preview never show the vault.
        if (!SCREENSHOTS_ALLOWED) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        setContent {
            CompositionLocalProvider(LocalHostActivity provides this) {
                SAOGalleryTheme {
                val viewModel: GalleryViewModel = viewModel()
                val state by viewModel.uiState.collectAsState()
                val transfer by viewModel.transfer.collectAsState()
                GalleryScreen(
                    state = state,
                    onDragStart = viewModel::onDragStart,
                    onDrag = viewModel::onDrag,
                    onDragEnd = viewModel::onDragEnd,
                    onShowIndex = viewModel::showIndex,
                    onImport = viewModel::importFromDevice,
                    onNextDelete = viewModel::nextDeleteStep,
                    onCancelDelete = viewModel::clearDeleteQueue,
                    onRemoveOriginals = viewModel::removeOriginals,
                    onKeepOriginals = viewModel::keepOriginals,
                    onRename = viewModel::rename,
                    onTrash = viewModel::moveToTrash,
                    onRestore = viewModel::restore,
                    onEmptyTrash = viewModel::emptyTrash,
                    onExport = viewModel::exportToPublic,
                    onBackup = viewModel::writeBackup,
                    onRestoreBackup = viewModel::readBackup,
                    onVerifyBackup = viewModel::verifyBackup,
                    trashExplained = viewModel::trashExplained,
                    onTrashExplained = viewModel::markTrashExplained,
                    transfer = transfer,
                    showOnboarding = showOnboarding,
                    onOnboardingDone = onboardingPrefs::markDone,
                )
                }
            }
        }
    }
}
