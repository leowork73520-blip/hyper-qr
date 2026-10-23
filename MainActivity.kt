@file:OptIn(ExperimentalMaterial3Api::class)

package com.hypertechdevai.hyperqr

import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.EnumMap
import java.util.Locale
import java.util.UUID

// =====================================================================
// 1. MODELS
// =====================================================================

enum class QrType(val displayName: String) {
    LINK("Link"),
    TEXT("Text"),
    WIFI("Wi-Fi"),
    CONTACT("Contact"),
    IMAGE("Image to QR")
}

data class QrItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val type: QrType,
    val foregroundColor: Int = 0xFF4A2E18.toInt(),
    val backgroundColor: Int = 0xFFFFFFFF.toInt(),
    // For logo QR codes this is an absolute file path inside the app's private storage
    val logoUri: String? = null,
    val size: Int = 1024,
    val createdAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val isScanned: Boolean = false
)

data class UserProfile(
    val name: String = "",
    val age: Int = 0,
    val isOnboarded: Boolean = false
)

// =====================================================================
// 2. REPOSITORIES
// =====================================================================

class ProfileRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("hyper_qr_profile_prefs", Context.MODE_PRIVATE)

    private val _profile = MutableStateFlow(loadProfile())
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    private fun loadProfile(): UserProfile {
        val name = prefs.getString(KEY_NAME, "") ?: ""
        val age = prefs.getInt(KEY_AGE, 0)
        val isOnboarded = prefs.getBoolean(KEY_IS_ONBOARDED, false)
        return UserProfile(name = name, age = age, isOnboarded = isOnboarded)
    }

    fun saveProfile(name: String, age: Int) {
        prefs.edit()
            .putString(KEY_NAME, name.trim())
            .putInt(KEY_AGE, age)
            .putBoolean(KEY_IS_ONBOARDED, true)
            .apply()
        _profile.value = UserProfile(name = name.trim(), age = age, isOnboarded = true)
    }

    companion object {
        private const val KEY_NAME = "user_name"
        private const val KEY_AGE = "user_age"
        private const val KEY_IS_ONBOARDED = "is_onboarded"
    }
}

class QrRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("hyper_qr_items_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private val _items = MutableStateFlow<List<QrItem>>(loadItems())
    val items: StateFlow<List<QrItem>> = _items.asStateFlow()

    private fun loadItems(): List<QrItem> {
        val json = prefs.getString(KEY_QR_LIST, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<QrItem>>() {}.type
            gson.fromJson<List<QrItem>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun persistItems(list: List<QrItem>) {
        val json = gson.toJson(list)
        prefs.edit().putString(KEY_QR_LIST, json).apply()
        _items.value = list
    }

    fun addItem(item: QrItem) {
        val current = _items.value.toMutableList()
        current.removeAll { it.id == item.id }
        current.add(0, item)
        persistItems(current)
    }

    fun toggleFavorite(itemId: String) {
        val updated = _items.value.map {
            if (it.id == itemId) it.copy(isFavorite = !it.isFavorite) else it
        }
        persistItems(updated)
    }

    fun deleteItem(itemId: String) {
        val updated = _items.value.filterNot { it.id == itemId }
        persistItems(updated)
    }

    companion object {
        private const val KEY_QR_LIST = "saved_qr_items_json"
    }
}

// =====================================================================
// 3. QR ENGINE + IMAGE + EXPORT UTILITIES
// =====================================================================

object QrCodeGenerator {

    fun generateQrBitmap(
        content: String,
        size: Int = 1024,
        foregroundColor: Int = 0xFF4A2E18.toInt(),
        backgroundColor: Int = 0xFFFFFFFF.toInt(),
        logoBitmap: Bitmap? = null
    ): Bitmap {
        val level = when {
            logoBitmap != null -> ErrorCorrectionLevel.H
            content.length <= 900 -> ErrorCorrectionLevel.Q
            content.length <= 1700 -> ErrorCorrectionLevel.M
            else -> ErrorCorrectionLevel.L
        }

        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java)
        hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        hints[EncodeHintType.ERROR_CORRECTION] = level
        hints[EncodeHintType.MARGIN] = 2

        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)

        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)

        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) foregroundColor else backgroundColor
            }
        }

        val qrBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        qrBitmap.setPixels(pixels, 0, width, 0, 0, width, height)

        return if (logoBitmap != null) {
            embedLogo(qrBitmap, logoBitmap, backgroundColor)
        } else {
            qrBitmap
        }
    }

    private fun embedLogo(baseQr: Bitmap, rawLogo: Bitmap, bgColor: Int): Bitmap {
        val result = baseQr.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val qrWidth = baseQr.width
        val qrHeight = baseQr.height
        val logoSize = (qrWidth * 0.22f).toInt()
        val logoLeft = (qrWidth - logoSize) / 2f
        val logoTop = (qrHeight - logoSize) / 2f

        val padding = logoSize * 0.12f
        val badgeRect = RectF(
            logoLeft - padding,
            logoTop - padding,
            logoLeft + logoSize + padding,
            logoTop + logoSize + padding
        )
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        badgePaint.color = bgColor
        badgePaint.style = Paint.Style.FILL
        val radius = logoSize * 0.25f
        canvas.drawRoundRect(badgeRect, radius, radius, badgePaint)

        // center-crop logo to a square so it never looks stretched
        val side = minOf(rawLogo.width, rawLogo.height)
        val square = Bitmap.createBitmap(
            rawLogo,
            (rawLogo.width - side) / 2,
            (rawLogo.height - side) / 2,
            side,
            side
        )
        val scaledLogo = Bitmap.createScaledBitmap(square, logoSize, logoSize, true)
        val roundedLogo = getRoundedBitmap(scaledLogo, radius * 0.8f)
        canvas.drawBitmap(roundedLogo, logoLeft, logoTop, null)

        return result
    }

    private fun getRoundedBitmap(bitmap: Bitmap, cornerRadius: Float): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, bitmap.width, bitmap.height)
        val rectF = RectF(rect)

        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        return output
    }
}

object ImageQrUtil {

    // ~2000 bytes of PNG -> ~2670 base64 chars, which fits in a QR (error level L)
    const val MAX_QR_DATA_BYTES = 2000

    fun loadBitmapSafely(context: Context, uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val maxDim = maxOf(info.size.width, info.size.height)
                    if (maxDim > 1024) {
                        decoder.setTargetSampleSize(maxDim / 1024 + 1)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun saveLogoToInternal(context: Context, bitmap: Bitmap): String {
        val dir = File(context.filesDir, "logos")
        dir.mkdirs()
        val maxSide = 400
        val biggest = maxOf(bitmap.width, bitmap.height)
        val scaled = if (biggest > maxSide) {
            val ratio = maxSide.toFloat() / biggest.toFloat()
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }
        val file = File(dir, "logo_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file.absolutePath
    }

    fun loadLogoFromPath(path: String): Bitmap? {
        return try {
            BitmapFactory.decodeFile(path)
        } catch (e: Exception) {
            null
        }
    }

    private fun scaleDown(src: Bitmap, target: Int): Bitmap {
        if (src.width <= target && src.height <= target) return src
        val ratio = src.width.toFloat() / src.height.toFloat()
        val w: Int
        val h: Int
        if (ratio >= 1f) {
            w = target
            h = (target / ratio).toInt().coerceAtLeast(1)
        } else {
            h = target
            w = (target * ratio).toInt().coerceAtLeast(1)
        }
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    fun encodeToDataUri(original: Bitmap): Result<String> {
        return try {
            val sizes = listOf(64, 48, 32, 24, 16)
            for (target in sizes) {
                val stream = ByteArrayOutputStream()
                scaleDown(original, target).compress(Bitmap.CompressFormat.PNG, 100, stream)
                val bytes = stream.toByteArray()
                if (bytes.size <= MAX_QR_DATA_BYTES) {
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    return Result.success("data:image/png;base64,$base64")
                }
            }
            Result.failure(
                Exception("Image is too detailed to fit inside a QR. Use 'Image Logo QR' mode or pick a simple icon.")
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

object FileExportUtil {

    fun saveQrToGallery(context: Context, bitmap: Bitmap, title: String): Result<Uri> {
        return try {
            val sanitized = title.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
            val filename = "HyperQR_${sanitized}_${System.currentTimeMillis()}.png"
            var outputStream: OutputStream? = null
            var imageUri: Uri? = null

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues()
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                contentValues.put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/HyperQR"
                )
                val resolver = context.contentResolver
                imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (imageUri != null) {
                    outputStream = resolver.openOutputStream(imageUri)
                }
            } else {
                @Suppress("DEPRECATION")
                val imagesDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "HyperQR"
                )
                if (!imagesDir.exists()) {
                    imagesDir.mkdirs()
                }
                val imageFile = File(imagesDir, filename)
                outputStream = FileOutputStream(imageFile)
                imageUri = Uri.fromFile(imageFile)
            }

            outputStream?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }

            if (imageUri != null) {
                Result.success(imageUri)
            } else {
                Result.failure(Exception("Could not create image file in gallery"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun shareQrBitmap(context: Context, bitmap: Bitmap, title: String, content: String) {
        try {
            val cachePath = File(context.cacheDir, "qr_codes")
            if (!cachePath.exists()) {
                cachePath.mkdirs()
            }
            val file = File(cachePath, "hyper_qr_shared.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val uri = Uri.parse("content://${context.packageName}.share/hyper_qr_shared.png")

            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "image/png"
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
            if (content.length < 500) {
                shareIntent.putExtra(Intent.EXTRA_TEXT, "Generated with Hyper QR: $title\n$content")
            }
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            context.startActivity(Intent.createChooser(shareIntent, "Share Hyper QR Code"))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/**
 * Tiny content provider used only to share the generated QR PNG from the cache folder.
 * (No XML file needed, so the project stays at 5 files.)
 */
class QrShareProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    private fun fileFor(uri: Uri): File {
        val name = File(uri.lastPathSegment ?: "x.png").name
        return File(File(context!!.cacheDir, "qr_codes"), name)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val file = fileFor(uri)
        val cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
        cursor.addRow(arrayOf<Any>(file.name, file.length()))
        return cursor
    }

    override fun getType(uri: Uri): String? = "image/png"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val file = fileFor(uri)
        if (!file.exists()) throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }
}

// =====================================================================
// 4. THEME
// =====================================================================

val CreamBackground = Color(0xFFFDFBF7)
val CreamSurface = Color(0xFFF7F2EB)
val CreamBorder = Color(0xFFE5DDD0)
val CardBorder = Color(0xFFE5DDD0)
val PureWhite = Color(0xFFFFFFFF)
val BrownPrimary = Color(0xFF4A2E18)
val BrownDark = Color(0xFF2B180B)
val AccentGold = Color(0xFFC98A41)
val TextPrimary = Color(0xFF2D2319)
val TextSecondary = Color(0xFF7C6E60)
val ErrorRed = Color(0xFFB3261E)
val SuccessGreen = Color(0xFF2D7A38)

@Composable
fun HyperQRTheme(content: @Composable () -> Unit) {
    val colors = lightColorScheme(
        primary = BrownPrimary,
        onPrimary = PureWhite,
        secondary = AccentGold,
        background = CreamBackground,
        surface = CreamBackground,
        onSurface = TextPrimary,
        onBackground = TextPrimary
    )
    MaterialTheme(colorScheme = colors, content = content)
}

// =====================================================================
// 5. SMALL REUSABLE UI PIECES
// =====================================================================

@Composable
fun HyperQrTopBar(
    title: String,
    canNavigateBack: Boolean = false,
    onNavigateBack: () -> Unit = {},
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = { Text(text = title, fontWeight = FontWeight.Bold, color = BrownDark) },
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = BrownPrimary
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = CreamBackground)
    )
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = BrownPrimary,
    unfocusedBorderColor = CreamBorder,
    focusedLabelColor = BrownPrimary,
    cursorColor = BrownPrimary,
    focusedContainerColor = PureWhite,
    unfocusedContainerColor = PureWhite
)

@Composable
fun HqField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(14.dp),
        colors = fieldColors()
    )
}

val FgPalette = listOf(
    0xFF4A2E18.toInt(),
    0xFF2B180B.toInt(),
    0xFF1F2937.toInt(),
    0xFF1E3A5F.toInt(),
    0xFF2D4A22.toInt(),
    0xFF8B2500.toInt()
)

val BgPalette = listOf(
    0xFFFFFFFF.toInt(),
    0xFFFDFBF7.toInt(),
    0xFFF7F2EB.toInt(),
    0xFFEFE7DC.toInt()
)

val SizeOptions = listOf(512, 1024, 2048)

@Composable
fun ColorSwatchRow(label: String, colors: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Column {
        Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            colors.forEach { c ->
                val isSelected = c == selected
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(c))
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) AccentGold else CreamBorder,
                            shape = CircleShape
                        )
                        .clickable { onSelect(c) }
                )
            }
        }
    }
}

@Composable
fun StyleOptions(
    fg: Int,
    bg: Int,
    size: Int,
    onFg: (Int) -> Unit,
    onBg: (Int) -> Unit,
    onSize: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ColorSwatchRow("QR color", FgPalette, fg, onFg)
        ColorSwatchRow("Background color", BgPalette, bg, onBg)
        Column {
            Text(text = "Image size", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SizeOptions.forEach { s ->
                    FilterChip(
                        selected = s == size,
                        onClick = { onSize(s) },
                        label = { Text("$s px") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrownPrimary,
                            selectedLabelColor = PureWhite
                        )
                    )
                }
            }
        }
    }
}

fun iconForType(type: QrType): ImageVector {
    return when (type) {
        QrType.LINK -> Icons.Filled.Link
        QrType.TEXT -> Icons.Filled.TextFields
        QrType.WIFI -> Icons.Filled.Wifi
        QrType.CONTACT -> Icons.Filled.Person
        QrType.IMAGE -> Icons.Filled.AddPhotoAlternate
    }
}

// =====================================================================
// 6. SCREENS
// =====================================================================

@Composable
fun OnboardingScreen(onDone: (String, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var ageText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CreamBackground)
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(BrownPrimary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.QrCode,
                contentDescription = null,
                tint = CreamBackground,
                modifier = Modifier.size(52.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text("Welcome to Hyper QR", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = BrownDark)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Create beautiful QR codes in seconds",
            fontSize = 14.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(28.dp))

        HqField(value = name, onValueChange = { name = it }, label = "Your name")
        Spacer(modifier = Modifier.height(12.dp))
        HqField(
            value = ageText,
            onValueChange = { v -> ageText = v.filter { ch -> ch.isDigit() }.take(3) },
            label = "Your age",
            keyboardType = KeyboardType.Number
        )

        if (error != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = error ?: "", color = ErrorRed, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(22.dp))
        Button(
            onClick = {
                val age = ageText.toIntOrNull()
                if (name.isBlank()) {
                    error = "Please enter your name."
                } else if (age == null || age < 1 || age > 120) {
                    error = "Please enter a valid age."
                } else {
                    onDone(name, age)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrownPrimary, contentColor = PureWhite)
        ) {
            Text("Get Started", fontWeight = FontWeight.Bold)
        }
    }
}

fun escapeWifi(s: String): String {
    val sb = StringBuilder()
    for (ch in s) {
        if (ch == '\\' || ch == ';' || ch == ',' || ch == ':' || ch == '"') {
            sb.append('\\')
        }
        sb.append(ch)
    }
    return sb.toString()
}

@Composable
fun CreateScreen(
    userName: String,
    onOpenImageQr: () -> Unit,
    onGenerate: (QrItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedType by remember { mutableStateOf(QrType.LINK) }
    var link by remember { mutableStateOf("https://") }
    var text by remember { mutableStateOf("") }
    var ssid by remember { mutableStateOf("") }
    var wifiPass by remember { mutableStateOf("") }
    var contactName by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }
    var contactEmail by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var fg by remember { mutableIntStateOf(FgPalette[0]) }
    var bg by remember { mutableIntStateOf(BgPalette[0]) }
    var size by remember { mutableIntStateOf(1024) }
    var error by remember { mutableStateOf<String?>(null) }

    val types = listOf(QrType.LINK, QrType.TEXT, QrType.WIFI, QrType.CONTACT)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (userName.isBlank()) "Create a QR code" else "Hello, $userName",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = BrownDark
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            types.forEach { t ->
                FilterChip(
                    selected = t == selectedType,
                    onClick = {
                        selectedType = t
                        error = null
                    },
                    label = { Text(t.displayName) },
                    leadingIcon = {
                        Icon(
                            imageVector = iconForType(t),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BrownPrimary,
                        selectedLabelColor = PureWhite,
                        selectedLeadingIconColor = PureWhite
                    )
                )
            }
        }

        when (selectedType) {
            QrType.LINK -> {
                HqField(link, { link = it }, "Website link", keyboardType = KeyboardType.Uri)
            }
            QrType.TEXT -> {
                HqField(text, { text = it }, "Your text", singleLine = false, minLines = 4)
            }
            QrType.WIFI -> {
                HqField(ssid, { ssid = it }, "Wi-Fi name (SSID)")
                HqField(wifiPass, { wifiPass = it }, "Wi-Fi password (leave empty if open)")
            }
            QrType.CONTACT -> {
                HqField(contactName, { contactName = it }, "Full name")
                HqField(contactPhone, { contactPhone = it }, "Phone number", keyboardType = KeyboardType.Phone)
                HqField(contactEmail, { contactEmail = it }, "Email", keyboardType = KeyboardType.Email)
            }
            QrType.IMAGE -> {
            }
        }

        HqField(title, { title = it }, "QR name (optional)")

        StyleOptions(
            fg = fg,
            bg = bg,
            size = size,
            onFg = { fg = it },
            onBg = { bg = it },
            onSize = { size = it }
        )

        if (error != null) {
            Text(text = error ?: "", color = ErrorRed, fontSize = 13.sp)
        }

        Button(
            onClick = {
                var content: String? = null
                when (selectedType) {
                    QrType.LINK -> {
                        val l = link.trim()
                        if (l.isNotEmpty() && l != "https://") content = l
                    }
                    QrType.TEXT -> {
                        val t = text.trim()
                        if (t.isNotEmpty()) content = t
                    }
                    QrType.WIFI -> {
                        if (ssid.isNotBlank()) {
                            val sec = if (wifiPass.isEmpty()) "nopass" else "WPA"
                            content = "WIFI:T:$sec;S:${escapeWifi(ssid)};P:${escapeWifi(wifiPass)};;"
                        }
                    }
                    QrType.CONTACT -> {
                        if (contactName.isNotBlank() || contactPhone.isNotBlank()) {
                            val sb = StringBuilder()
                            sb.append("BEGIN:VCARD\nVERSION:3.0\n")
                            if (contactName.isNotBlank()) sb.append("FN:").append(contactName.trim()).append("\n")
                            if (contactPhone.isNotBlank()) sb.append("TEL:").append(contactPhone.trim()).append("\n")
                            if (contactEmail.isNotBlank()) sb.append("EMAIL:").append(contactEmail.trim()).append("\n")
                            sb.append("END:VCARD")
                            content = sb.toString()
                        }
                    }
                    QrType.IMAGE -> {
                    }
                }

                if (content == null) {
                    error = "Please fill in the required details first."
                } else {
                    error = null
                    val finalTitle = if (title.isBlank()) "${selectedType.displayName} QR" else title.trim()
                    onGenerate(
                        QrItem(
                            title = finalTitle,
                            content = content,
                            type = selectedType,
                            foregroundColor = fg,
                            backgroundColor = bg,
                            size = size
                        )
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrownPrimary, contentColor = PureWhite)
        ) {
            Icon(imageVector = Icons.Filled.QrCode, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Generate QR", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenImageQr() },
            colors = CardDefaults.cardColors(containerColor = CreamSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.AddPhotoAlternate,
                    contentDescription = null,
                    tint = BrownPrimary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Image to QR", fontWeight = FontWeight.Bold, color = BrownDark)
                    Text(
                        "Put your logo inside a QR or turn a tiny image into a QR",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun LibraryScreen(
    items: List<QrItem>,
    onOpen: (QrItem) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var onlyFavorites by remember { mutableStateOf(false) }
    val shown = if (onlyFavorites) items.filter { it.isFavorite } else items

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = !onlyFavorites,
                onClick = { onlyFavorites = false },
                label = { Text("All") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BrownPrimary,
                    selectedLabelColor = PureWhite
                )
            )
            FilterChip(
                selected = onlyFavorites,
                onClick = { onlyFavorites = true },
                label = { Text("Favorites") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BrownPrimary,
                    selectedLabelColor = PureWhite
                )
            )
        }

        if (shown.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (onlyFavorites) "No favorites yet" else "No QR codes yet. Create your first one!",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(shown, key = { it.id }) { item ->
                    val dateText = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                        .format(Date(item.createdAt))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpen(item) },
                        colors = CardDefaults.cardColors(containerColor = PureWhite),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CreamSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = iconForType(item.type),
                                    contentDescription = null,
                                    tint = BrownPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    fontWeight = FontWeight.Bold,
                                    color = BrownDark,
                                    maxLines = 1
                                )
                                Text(
                                    text = item.type.displayName + " • " + dateText,
                                    fontSize = 12.sp,
                                    color = TextSecondary,
                                    maxLines = 1
                                )
                            }
                            IconButton(onClick = { onToggleFavorite(item.id) }) {
                                Icon(
                                    imageVector = if (item.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (item.isFavorite) ErrorRed else TextSecondary
                                )
                            }
                            IconButton(onClick = { onDelete(item.id) }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete",
                                    tint = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class ImageQrMode(val title: String, val description: String) {
    LOGO("Image Logo QR", "Put your image in the center of a QR for any link or text"),
    DATA("Image Data QR", "Store a tiny image directly inside the QR (simple icons only)")
}

@Composable
fun ImageToQrScreen(
    onNavigateBack: () -> Unit,
    onQrGenerated: (QrItem) -> Unit
) {
    val context = LocalContext.current

    var mode by remember { mutableStateOf(ImageQrMode.LOGO) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var contentText by remember { mutableStateOf("https://") }
    var fg by remember { mutableIntStateOf(FgPalette[0]) }
    var bg by remember { mutableIntStateOf(BgPalette[0]) }
    var size by remember { mutableIntStateOf(1024) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val bmp = ImageQrUtil.loadBitmapSafely(context, uri)
            if (bmp != null) {
                selectedBitmap = bmp
                errorMessage = null
            } else {
                errorMessage = "Could not open the selected image."
            }
        }
    }

    Scaffold(
        topBar = {
            HyperQrTopBar(title = "Image to QR", canNavigateBack = true, onNavigateBack = onNavigateBack)
        },
        containerColor = CreamBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImageQrMode.values().forEach { m ->
                    FilterChip(
                        selected = m == mode,
                        onClick = {
                            mode = m
                            errorMessage = null
                        },
                        label = { Text(m.title) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BrownPrimary,
                            selectedLabelColor = PureWhite
                        )
                    )
                }
            }
            Text(text = mode.description, fontSize = 13.sp, color = TextSecondary)

            val preview = selectedBitmap
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CreamSurface)
                    .border(1.dp, CreamBorder, RoundedCornerShape(16.dp))
                    .clickable {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (preview != null) {
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = "Selected image",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.AddPhotoAlternate,
                            contentDescription = null,
                            tint = BrownPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text("Tap to choose an image", color = TextSecondary, fontSize = 13.sp)
                    }
                }
            }

            if (mode == ImageQrMode.LOGO) {
                HqField(
                    value = contentText,
                    onValueChange = { contentText = it },
                    label = "Link or text for the QR",
                    keyboardType = KeyboardType.Uri
                )
            } else {
                Text(
                    text = "The image will be shrunk to a tiny size so it can fit inside the QR. Works best for simple icons.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            StyleOptions(
                fg = fg,
                bg = bg,
                size = size,
                onFg = { fg = it },
                onBg = { bg = it },
                onSize = { size = it }
            )

            if (errorMessage != null) {
                Text(text = errorMessage ?: "", color = ErrorRed, fontSize = 13.sp)
            }

            Button(
                onClick = {
                    val bmp = selectedBitmap
                    if (bmp == null) {
                        errorMessage = "Please choose an image first."
                    } else if (mode == ImageQrMode.LOGO) {
                        val t = contentText.trim()
                        if (t.isEmpty() || t == "https://") {
                            errorMessage = "Please enter a link or text."
                        } else {
                            try {
                                val path = ImageQrUtil.saveLogoToInternal(context, bmp)
                                errorMessage = null
                                onQrGenerated(
                                    QrItem(
                                        title = "Logo QR",
                                        content = t,
                                        type = QrType.IMAGE,
                                        foregroundColor = fg,
                                        backgroundColor = bg,
                                        logoUri = path,
                                        size = size
                                    )
                                )
                            } catch (e: Exception) {
                                errorMessage = "Could not save logo: ${e.message}"
                            }
                        }
                    } else {
                        val res = ImageQrUtil.encodeToDataUri(bmp)
                        val dataUri = res.getOrNull()
                        if (dataUri != null) {
                            errorMessage = null
                            onQrGenerated(
                                QrItem(
                                    title = "Image Data QR",
                                    content = dataUri,
                                    type = QrType.IMAGE,
                                    foregroundColor = fg,
                                    backgroundColor = bg,
                                    size = size
                                )
                            )
                        } else {
                            errorMessage = res.exceptionOrNull()?.message ?: "Could not encode image."
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrownPrimary, contentColor = PureWhite)
            ) {
                Text("Generate QR", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun ResultScreen(
    qrItem: QrItem,
    initialFavorite: Boolean,
    onNavigateBack: () -> Unit,
    onDelete: (String) -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    val context = LocalContext.current
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isFavorite by remember { mutableStateOf(initialFavorite) }
    var saveStatusMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(qrItem.id) {
        val outcome = withContext(Dispatchers.Default) {
            runCatching {
                val logo: Bitmap? = qrItem.logoUri?.let { ImageQrUtil.loadLogoFromPath(it) }
                QrCodeGenerator.generateQrBitmap(
                    content = qrItem.content,
                    size = qrItem.size,
                    foregroundColor = qrItem.foregroundColor,
                    backgroundColor = qrItem.backgroundColor,
                    logoBitmap = logo
                )
            }
        }
        val bmp = outcome.getOrNull()
        if (bmp != null) {
            qrBitmap = bmp
        } else {
            errorText = "Could not create this QR. The content may be too long."
        }
    }

    val saveNow: () -> Unit = {
        val bmp = qrBitmap
        if (bmp != null) {
            val result = FileExportUtil.saveQrToGallery(context, bmp, qrItem.title)
            if (result.isSuccess) {
                saveStatusMessage = "Saved to Pictures / HyperQR"
                Toast.makeText(context, "Saved PNG to Gallery!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(
                    context,
                    "Save failed: ${result.exceptionOrNull()?.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            saveNow()
        } else {
            Toast.makeText(context, "Storage permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            HyperQrTopBar(
                title = "QR Result",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(onClick = {
                        isFavorite = !isFavorite
                        onToggleFavorite(qrItem.id)
                    }) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) ErrorRed else BrownPrimary
                        )
                    }
                }
            )
        },
        containerColor = CreamBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = PureWhite),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(3.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, CreamBorder, RoundedCornerShape(16.dp))
                            .background(PureWhite),
                        contentAlignment = Alignment.Center
                    ) {
                        val bmp = qrBitmap
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Generated QR Code",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (errorText != null) {
                            Text(
                                text = errorText ?: "",
                                color = ErrorRed,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp)
                            )
                        } else {
                            Text(text = "Creating QR...", color = TextSecondary, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = qrItem.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = BrownDark,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val dateFormatted = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
                        .format(Date(qrItem.createdAt))
                    Text(text = "Created: $dateFormatted", fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CreamSurface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = qrItem.content,
                            fontSize = 12.sp,
                            color = TextPrimary,
                            maxLines = 3,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            if (saveStatusMessage != null) {
                Text(
                    text = saveStatusMessage ?: "",
                    color = SuccessGreen,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            ) != PackageManager.PERMISSION_GRANTED
                        if (needsPermission) {
                            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else {
                            saveNow()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrownPrimary, contentColor = PureWhite)
                ) {
                    Icon(imageVector = Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save PNG", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Button(
                    onClick = {
                        val bmp = qrBitmap
                        if (bmp != null) {
                            FileExportUtil.shareQrBitmap(context, bmp, qrItem.title, qrItem.content)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrownDark, contentColor = PureWhite)
                ) {
                    Icon(imageVector = Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        isFavorite = !isFavorite
                        onToggleFavorite(qrItem.id)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        tint = if (isFavorite) ErrorRed else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isFavorite) "Favorited" else "Favorite",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }

                OutlinedButton(
                    onClick = { onDelete(qrItem.id) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Delete", color = ErrorRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

// =====================================================================
// 7. APP ROOT + NAVIGATION
// =====================================================================

sealed class Screen {
    object Create : Screen()
    object Library : Screen()
    object ImageQr : Screen()
    class Result(val item: QrItem, val backTo: Screen) : Screen()
}

@Composable
fun HyperQrApp(profileRepository: ProfileRepository, qrRepository: QrRepository) {
    val profile by profileRepository.profile.collectAsState()
    val items by qrRepository.items.collectAsState()
    var screen by remember { mutableStateOf<Screen>(Screen.Create) }

    val current = screen

    BackHandler(enabled = profile.isOnboarded && current !is Screen.Create) {
        screen = if (current is Screen.Result) current.backTo else Screen.Create
    }

    if (!profile.isOnboarded) {
        OnboardingScreen(onDone = { name, age -> profileRepository.saveProfile(name, age) })
    } else if (current is Screen.Result) {
        val liveFavorite = items.firstOrNull { it.id == current.item.id }?.isFavorite
            ?: current.item.isFavorite
        ResultScreen(
            qrItem = current.item,
            initialFavorite = liveFavorite,
            onNavigateBack = { screen = current.backTo },
            onDelete = { id ->
                qrRepository.deleteItem(id)
                screen = current.backTo
            },
            onToggleFavorite = { id -> qrRepository.toggleFavorite(id) }
        )
    } else if (current is Screen.ImageQr) {
        ImageToQrScreen(
            onNavigateBack = { screen = Screen.Create },
            onQrGenerated = { item ->
                qrRepository.addItem(item)
                screen = Screen.Result(item, Screen.Create)
            }
        )
    } else {
        Scaffold(
            topBar = {
                HyperQrTopBar(title = if (current is Screen.Library) "My Library" else "Hyper QR")
            },
            bottomBar = {
                NavigationBar(containerColor = PureWhite) {
                    val itemColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PureWhite,
                        selectedTextColor = BrownPrimary,
                        indicatorColor = BrownPrimary,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    )
                    NavigationBarItem(
                        selected = current is Screen.Create,
                        onClick = { screen = Screen.Create },
                        icon = { Icon(Icons.Filled.QrCode, contentDescription = null) },
                        label = { Text("Create") },
                        colors = itemColors
                    )
                    NavigationBarItem(
                        selected = current is Screen.Library,
                        onClick = { screen = Screen.Library },
                        icon = { Icon(Icons.Filled.History, contentDescription = null) },
                        label = { Text("Library") },
                        colors = itemColors
                    )
                }
            },
            containerColor = CreamBackground
        ) { innerPadding ->
            if (current is Screen.Library) {
                LibraryScreen(
                    items = items,
                    onOpen = { item -> screen = Screen.Result(item, Screen.Library) },
                    onToggleFavorite = { id -> qrRepository.toggleFavorite(id) },
                    onDelete = { id -> qrRepository.deleteItem(id) },
                    modifier = Modifier.padding(innerPadding)
                )
            } else {
                CreateScreen(
                    userName = profile.name,
                    onOpenImageQr = { screen = Screen.ImageQr },
                    onGenerate = { item ->
                        qrRepository.addItem(item)
                        screen = Screen.Result(item, Screen.Create)
                    },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

// =====================================================================
// 8. ACTIVITY
// =====================================================================

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val profileRepository = ProfileRepository(applicationContext)
        val qrRepository = QrRepository(applicationContext)

        setContent {
            HyperQRTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = CreamBackground
                ) {
                    HyperQrApp(
                        profileRepository = profileRepository,
                        qrRepository = qrRepository
                    )
                }
            }
        }
    }
}
