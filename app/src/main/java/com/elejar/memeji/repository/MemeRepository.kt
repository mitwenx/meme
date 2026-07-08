package com.elejar.memeji.repository

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.elejar.memeji.data.AppInfo
import com.elejar.memeji.data.CategoryItem
import com.elejar.memeji.data.Meme
import com.elejar.memeji.data.remote.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.regex.Pattern

class MemeRepository(
    private val apiService: ApiService,
    private val context: Context,
    private val okHttpClient: OkHttpClient // Kept if needed for other generic calls, though Zip logic removed
) {

    private var allMemesCache: List<Meme>? = null
    private val categoryImageTagSuffix = "-categorie"

    companion object {
        const val SENSITIVE_TAG = "18+"
    }

    data class ShareableData(val uri: Uri, val mimeType: String?)

    fun isCacheEmpty(): Boolean {
        return allMemesCache == null
    }

    suspend fun getMemes(forceRefresh: Boolean = false): Result<List<Meme>> {
        return try {
            if (forceRefresh) {
                allMemesCache = null
                Log.d("MemeRepository", "Cache cleared due to forceRefresh=true")
            }

            if (allMemesCache != null) {
                return Result.success(allMemesCache!!)
            }

            Log.d("MemeRepository", "Fetching memes from network.")
            val response = apiService.getMemes()
            if (response.isSuccessful && response.body() != null) {
                val memes = response.body()!!
                allMemesCache = memes
                Result.success(memes)
            } else {
                Result.failure(Exception("Error fetching memes: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Log.e("MemeRepository", "Network error fetching memes", e)
            Result.failure(e)
        }
    }

    suspend fun getAppUpdateInfo(): Result<AppInfo> {
        return try {
            val response = apiService.getAppUpdateInfo()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Error fetching update info"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getCategoriesWithImages(): List<CategoryItem> {
        val cache = allMemesCache ?: return emptyList()

        val distinctCategories = cache.flatMap { it.tags }
            .filter {
                !it.endsWith(categoryImageTagSuffix, ignoreCase = true) &&
                        !it.equals(SENSITIVE_TAG, ignoreCase = true)
            }
            .distinctBy { it.lowercase() }
            .sorted()

        return distinctCategories.map { categoryName ->
            val targetTag = "$categoryName$categoryImageTagSuffix"

            val representativeMeme = cache.find { meme ->
                val hasBaseTag = meme.tags.any { it.equals(categoryName, ignoreCase = true) }
                val hasSpecificTag = meme.tags.any { it.equals(targetTag, ignoreCase = true) }
                val isSensitive = meme.tags.any { it.equals(SENSITIVE_TAG, ignoreCase = true) }

                hasBaseTag && hasSpecificTag && !isSensitive
            }

            val imageUrl = representativeMeme?.url ?: cache.find { meme ->
                val hasBaseTag = meme.tags.any { it.equals(categoryName, ignoreCase = true) }
                val isSensitive = meme.tags.any { it.equals(SENSITIVE_TAG, ignoreCase = true) }
                hasBaseTag && !isSensitive
            }?.url

            CategoryItem(name = categoryName, imageUrl = imageUrl)
        }
    }

    fun getMemesByCategory(category: String): List<Meme> {
        return allMemesCache?.filter { meme -> meme.tags.any { it.equals(category, ignoreCase = true) } }
            ?: emptyList()
    }

    suspend fun downloadImageToCache(imageUrl: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(imageUrl).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("Download failed: ${response.code()}"))
            }
            val body = response.body ?: return@withContext Result.failure(IOException("Empty response body"))
            val cacheFile = File(context.cacheDir, "glide_cache_${System.nanoTime()}")
            FileOutputStream(cacheFile).use { output ->
                body.byteStream().use { input ->
                    input.copyTo(output)
                }
            }
            if (cacheFile.exists()) {
                Result.success(cacheFile)
            } else {
                Result.failure(IOException("Failed to download image"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getShareableUri(meme: Meme, fileFromCache: File): ShareableData? =
        withContext(Dispatchers.Main) {
            try {
                val cacheDir = File(context.cacheDir, "share_cache")
                cacheDir.mkdirs()

                val urlExtension = meme.url.substringAfterLast('.', "").lowercase(Locale.ROOT)
                val cacheExtension =
                    fileFromCache.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                val extension =
                    if (urlExtension.isNotEmpty() && urlExtension.length <= 4) urlExtension else cacheExtension

                val safeBaseName = makeFilenameSafe(meme.name)
                val targetFileName =
                    if (extension.isNotEmpty()) "$safeBaseName.$extension" else safeBaseName
                val targetFile = File(cacheDir, targetFileName)

                withContext(Dispatchers.IO) {
                    if (!targetFile.exists()) {
                        targetFile.createNewFile()
                    }
                    fileFromCache.inputStream().use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    targetFile
                )

                val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "image/*"
                ShareableData(uri, mimeType)

            } catch (e: Exception) {
                Log.e("MemeRepository", "Error preparing shareable URI", e)
                null
            }
        }


    private fun makeFilenameSafe(input: String): String {
        val pattern = Pattern.compile("[^a-zA-Z0-9-_]")
        val safe = pattern.matcher(input).replaceAll("_")
        return safe.take(100)
    }

    suspend fun downloadMemeDirect(meme: Meme): Result<String> = withContext(Dispatchers.IO) {
        try {
            val extension = meme.url.substringAfterLast('.', "jpg").lowercase(Locale.ROOT)
            val sanitizedName = makeFilenameSafe(meme.name)
            val fileName = "$sanitizedName.$extension"
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "image/jpeg"

            val request = Request.Builder().url(meme.url).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("Download failed: ${response.code()}"))
            }
            val body = response.body ?: return@withContext Result.failure(IOException("Empty response body"))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return@withContext Result.failure(IOException("Failed to create MediaStore entry"))

                resolver.openOutputStream(uri)?.use { output ->
                    body.byteStream().use { input -> input.copyTo(output) }
                } ?: return@withContext Result.failure(IOException("Failed to open output stream"))

                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)

                Result.success(fileName)
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                downloadsDir.mkdirs()
                val file = File(downloadsDir, fileName)
                var fileCounter = 1
                while (file.exists()) {
                    val nameWithoutExt = sanitizedName + "_$fileCounter"
                    val newFileName = "$nameWithoutExt.$extension"
                    val newFile = File(downloadsDir, newFileName)
                    if (!newFile.exists()) {
                        break
                    }
                    fileCounter++
                }
                val targetFile = if (file.exists()) {
                    File(downloadsDir, "${sanitizedName}_$fileCounter.$extension")
                } else {
                    file
                }

                FileOutputStream(targetFile).use { output ->
                    body.byteStream().use { input -> input.copyTo(output) }
                }

                Result.success(targetFile.absolutePath)
            }
        } catch (e: Exception) {
            Log.e("MemeRepository", "Error downloading meme: ${meme.name}", e)
            Result.failure(e)
        }
    }
}
