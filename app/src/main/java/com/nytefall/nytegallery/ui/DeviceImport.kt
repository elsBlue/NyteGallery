package com.nytefall.nytegallery.ui

import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val MovePink = Color(0xFFFF4D6A)

fun photoPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        android.Manifest.permission.READ_MEDIA_IMAGES,
        android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES)
    Build.VERSION.SDK_INT <= 28 -> arrayOf(
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
    )
    else -> arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
}

fun hasPhotoAccess(context: Context): Boolean {
    return photoPermissions().any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

data class DevicePhoto(val uri: Uri, val name: String, val folder: String)

private const val BUCKET_COLUMN = "bucket_display_name"

fun queryDevicePhotos(context: Context): List<DevicePhoto> {
    return try {
        readDevicePhotos(context, withFolder = true)
    } catch (_: IllegalArgumentException) {
        readDevicePhotos(context, withFolder = false)
    }
}

private fun readDevicePhotos(context: Context, withFolder: Boolean): List<DevicePhoto> {
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val projection = buildList {
        add(MediaStore.Images.Media._ID)
        add(MediaStore.Images.Media.DISPLAY_NAME)
        if (withFolder) add(BUCKET_COLUMN)
    }.toTypedArray()
    val found = mutableListOf<DevicePhoto>()
    context.contentResolver.query(
        collection,
        projection,
        null,
        null,
        "${MediaStore.Images.Media.DATE_ADDED} DESC",
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
        val folderColumn = if (withFolder) cursor.getColumnIndex(BUCKET_COLUMN) else -1
        while (cursor.moveToNext() && found.size < 4000) {
            val id = cursor.getLong(idColumn)
            val name = cursor.getString(nameColumn) ?: "Photo"
            val rawFolder = if (folderColumn >= 0) cursor.getString(folderColumn) else null
            val folder = rawFolder?.trim().takeUnless { it.isNullOrEmpty() } ?: "Pictures"
            found += DevicePhoto(ContentUris.withAppendedId(collection, id), name, folder)
        }
    }
    return found
}

@Composable
fun DeviceImportPage(
    light: Boolean,
    onClose: () -> Unit,
    onMove: (List<Uri>) -> Unit,
) {
    val context = LocalContext.current
    val ink = if (light) Color(0xFF2A2428) else Color.White
    val panel = if (light) Color(0xFFF7F4EF) else Color(0xFF12141A)
    val field = if (light) Color.White else Color(0xFF1C1E26)
    var photos by remember { mutableStateOf<List<DevicePhoto>>(emptyList()) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var mode by remember { mutableStateOf("all") }
    var openFolder by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        photos = withContext(Dispatchers.IO) { queryDevicePhotos(context) }
    }
    BackHandler(enabled = openFolder != null) {
        openFolder = null
    }
    val folders = remember(photos) {
        photos.groupBy { it.folder }.entries.toList()
    }
    val visible = if (mode == "folder" && openFolder != null) {
        photos.filter { it.folder == openFolder }
    } else {
        photos
    }
    Column(Modifier.fillMaxSize().background(panel).windowInsetsPadding(WindowInsets.systemBars)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Move into Nyte", color = ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            val scopeIds = if (mode == "folder" && openFolder != null) {
                visible.map { it.uri.toString() }
            } else {
                photos.map { it.uri.toString() }
            }
            val allOn = scopeIds.isNotEmpty() && scopeIds.all { it in selected }
            if (photos.isNotEmpty()) {
                Text(
                    if (allOn) "Clear" else "Select all",
                    color = ink.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    modifier = Modifier.clickable {
                        selected = if (allOn) selected - scopeIds.toSet() else selected + scopeIds
                    }.padding(8.dp),
                )
            }
            Text("Close", color = ink.copy(alpha = 0.7f), modifier = Modifier.clickable(onClick = onClose).padding(8.dp))
        }
        Text(
            "Chosen photos are encrypted into Nyte Gallery. Afterwards you are asked whether to remove the originals from your phone gallery or keep them.",
            color = ink.copy(alpha = 0.7f),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        if (photos.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ModeChip("All", mode == "all", light) {
                    mode = "all"
                    openFolder = null
                }
                ModeChip("Folder", mode == "folder", light) {
                    mode = "folder"
                }
                val opened = openFolder
                if (mode == "folder" && opened != null) {
                    Text(
                        "‹ $opened",
                        color = ink.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { openFolder = null }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
        }
        if (photos.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No photos to move yet.", color = ink.copy(alpha = 0.55f), textAlign = TextAlign.Center)
            }
        } else if (mode == "folder" && openFolder == null) {
            LazyVerticalGrid(
                columns = if (windowShape() == WindowShape.Phone) GridCells.Fixed(2) else GridCells.Adaptive(160.dp),
                modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp, start = 12.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(folders, key = { it.key }) { entry ->
                    val picked = entry.value.count { it.uri.toString() in selected }
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { openFolder = entry.key },
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(4f / 3f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(field),
                        ) {
                            DeviceThumb(entry.value.first().uri)
                            if (picked > 0) {
                                Box(
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .background(MovePink, CircleShape)
                                        .padding(horizontal = 7.dp, vertical = 2.dp),
                                ) {
                                    Text("$picked", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        Text(
                            "${entry.key} · ${entry.value.size}",
                            color = ink,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp, bottom = 2.dp),
                        )
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = vaultGridColumns(),
                modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp, start = 8.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(visible, key = { it.uri.toString() }) { photo ->
                    val key = photo.uri.toString()
                    val on = key in selected
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(field)
                            .clickable {
                                selected = if (on) selected - key else selected + key
                            },
                    ) {
                        DeviceThumb(photo.uri)
                        if (on) {
                            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(5.dp)
                                    .size(18.dp)
                                    .background(MovePink, CircleShape),
                            )
                        }
                    }
                }
            }
        }
        val count = selected.size
        Box(
            Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(if (count > 0) MovePink else ink.copy(alpha = 0.12f))
                .clickable(enabled = count > 0) {
                    onMove(photos.filter { it.uri.toString() in selected }.map { it.uri })
                }
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (count == 0) "Select photos" else "Move in ($count)",
                color = if (count > 0) Color.White else ink.copy(alpha = 0.4f),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ModeChip(label: String, active: Boolean, light: Boolean, onClick: () -> Unit) {
    val bg = when {
        active && light -> Color(0xFF2A2428)
        active -> Color.White
        light -> Color(0x0F000000)
        else -> Color(0x1AFFFFFF)
    }
    val fg = when {
        active && light -> Color.White
        active -> Color(0xFF1C1E26)
        light -> Color(0xFF5C463F)
        else -> Color.White.copy(alpha = 0.82f)
    }
    Text(
        label,
        color = fg,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun DeviceThumb(uri: Uri) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    context.contentResolver.loadThumbnail(uri, Size(240, 240), null)
                } else {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = 4 })
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
    }
    val image = bitmap
    if (image != null) {
        Image(image.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}
