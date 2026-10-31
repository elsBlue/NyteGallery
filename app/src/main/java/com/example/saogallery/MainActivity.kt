package com.example.saogallery

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.saogallery.ui.GalleryScreen
import com.example.saogallery.ui.GalleryViewModel
import com.example.saogallery.ui.LocalHostActivity
import com.example.saogallery.ui.theme.SAOGalleryTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                )
                }
            }
        }
    }
}
