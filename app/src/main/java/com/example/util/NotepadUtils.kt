package com.example.util

import android.content.Context
import android.os.Environment
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

object AutoSortDetector {
    // Regex for API Keys: typically sk-..., ghp_..., AIzaSy..., or strong alphanumeric strings >= 24 chars
    private val apiKeyRegex = Regex("(sk-[a-zA-Z0-9]{20,})|(sk-proj-[a-zA-Z0-9]{30,})|(AIzaSy[a-zA-Z0-9_-]{33})|(ghp_[a-zA-Z0-9]{36,})")

    // Simple code detection keywords or structures
    private val codeKeywords = listOf(
        "class ", "public ", "private ", "void ", "import ", "def ", "fun ", "val ", "var ",
        "<html>", "</html>", "<body>", "</div>", "css", "iostream", "#include", "std::",
        "<?php", "select ", "from ", "where ", "insert ", "lambda", "const ", "let ", "console.log"
    )

    fun detectType(title: String, content: String): String {
        val fullText = "$title\n$content"

        // 1. Detect links or video references
        if (fullText.contains("http://") || fullText.contains("https://") || 
            fullText.contains("www.") || fullText.contains(".mp4") || 
            fullText.contains("youtube.com") || fullText.contains("youtu.be")) {
            return "VIDEO"
        }

        // 2. Detect API Key
        if (apiKeyRegex.containsMatchIn(fullText) || 
            fullText.contains("api_key", ignoreCase = true) ||
            fullText.contains("apikey", ignoreCase = true) ||
            fullText.contains("api_token", ignoreCase = true) ||
            fullText.contains("secret", ignoreCase = true) ||
            fullText.contains("token", ignoreCase = true) ||
            fullText.contains("passwd", ignoreCase = true) ||
            fullText.contains("password", ignoreCase = true) ||
            fullText.contains("credential", ignoreCase = true) ||
            fullText.contains("key", ignoreCase = true) ||
            fullText.contains("api", ignoreCase = true) ||
            fullText.contains("openai", ignoreCase = true) ||
            fullText.contains("gemini", ignoreCase = true) ||
            fullText.contains("anthropic", ignoreCase = true) ||
            fullText.contains("claude", ignoreCase = true) ||
            fullText.contains("github", ignoreCase = true) ||
            fullText.contains("token", ignoreCase = true)) {
            return "API"
        }

        // 3. Detect Code blocks
        var codeCount = 0
        for (kw in codeKeywords) {
            if (fullText.contains(kw, ignoreCase = true)) {
                codeCount++
            }
        }
        val curlyBracesCount = content.count { it == '{' || it == '}' }
        val semiColonCount = content.count { it == ';' }
        
        if (codeCount >= 2 || (curlyBracesCount >= 2 && semiColonCount >= 2) || content.contains("```")) {
            return "CODE"
        }

        return "NORMAL"
    }
}

object NameStylizer {
    fun getStylishFonts(name: String): List<Pair<String, String>> {
        if (name.isEmpty()) return emptyList()
        val styles = mutableListOf<Pair<String, String>>()

        // Standard conversions mapping chars
        val bubble = name.map { c ->
            when (c) {
                in 'a'..'z' -> (c.code - 'a'.code + 0x24D0).toChar().toString()
                in 'A'..'Z' -> (c.code - 'A'.code + 0x24B6).toChar().toString()
                else -> c.toString()
            }
        }.joinToString("")
        styles.add("Bubble Text" to bubble)

        val gothic = name.map { c ->
            when {
                c.isLowerCase() -> (c.code - 'a'.code + 0x1D520) // Math Fraktur
                c.isUpperCase() -> (c.code - 'A'.code + 0x1D504)
                else -> c.code
            }
        } // we will use fallback clean generators or direct mappings to look amazing
        styles.add("Gothic Style" to translateChars(name, "𝔄𝔅𝔆𝔇𝔈𝔉𝔊𝔋𝔌𝔍𝔎𝔏𝔐𝔑𝔒𝔓𝔔𝔕𝔖𝔗𝔘𝔙𝔚𝔛𝔜𝔝", "𝔞𝔟𝔠𝔡𝔢𝔣𝔤𝔥𝔦𝔨𝔩𝔩𝔪𝔫𝔬𝔭𝔮𝔯𝔰𝔱𝔲𝔳𝔴𝔵𝔶𝔷"))
        styles.add("Script Bold" to translateChars(name, "𝓐𝓑𝓒𝓓𝓔𝓕𝓖𝓗𝓘𝓙𝓚𝓛𝓜𝓝𝓞𝓟𝓠𝓡𝓢𝓣𝓤𝓥𝓦𝓧𝓨𝓩", "𝓪𝓫𝓬𝓭𝓮𝓯𝓰𝓱𝓲𝓳𝓴𝓵𝓶𝓷𝓸𝓹𝓺𝓻𝓼𝓽𝓾𝓿𝔀𝔁𝔂𝔃"))
        styles.add("Double-Struck" to translateChars(name, "𝔸𝔹ℂ𝔻𝔼𝔽𝔾ℍ𝕀𝕁𝕂𝕃𝕄ℕ𝕆ℙℚℝ𝕊𝕋𝕌𝕍𝕎𝕏𝕐ℤ", "𝕒𝕓𝕔𝕕𝕖𝕗𝕘𝕙𝕚𝕛𝕜𝕝𝕞𝕟𝕠𝕡𝕢𝕣𝕤𝕥𝕦𝕧𝕨𝕩𝕪𝕫"))
        styles.add("Classic Boxed" to translateChars(name, "🄰🄱🄲🄳🄴🄵🄶🄷🄸🄹🄺🄻🄼🄽🄾🄿🅀🅁🅂🅃🅄🅅🅆🅇🅈🅉", "🄰🄱🄲🄳🄴🄵🄶🄷🄸🄹🄺🄻🄼🄽🄾🄿🅀🅁🅂🅃🅄🅅🅆🅇🅈🅉"))
        styles.add("Dark Boxed" to translateChars(name, "🅰🅱🅲🅳🅴🅵🅶🅷🅸🅿🅻🅼🅽🅾🅿🆀🆁🆂🆃🆄🆅🆆🆇🆈🆉", "🅰🅱🅲🅳🅴🅵🅶🅷🅸🅿🅻🅼🅽🅾🅿🆀🆁🆂🆃🆄🆅🆆🆇🆈🆉"))
        styles.add("Small Caps" to translateChars(name, "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴩqʀꜱᴛᴜᴠᴡxʏᴢ", "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴩqʀ<b>ꜱᴛᴜᴠᴡxʏᴢ"))
        styles.add("Upside Down" to flipper(name))
        styles.add("Parenthesized" to name.map { "($it)" }.joinToString(""))
        styles.add("Mirror Image" to translateChars(name, "AᙡƆbƎߎGHIᒐK_MИOԳЯƧTUVWXYZ", "ɒdɔbɘᎸgʜiᒐʞlmnopqɿꙅƚυvwxγƨ"))
        styles.add("Heart Sparkle" to "ღ $name ღ")
        styles.add("Stars & Stripes" to "★彡 $name 彡★")
        styles.add("Glitched Matrix" to name.map { "$it̷" }.joinToString(""))
        styles.add("Royal Wings" to "꧁༒• $name •༒꧂")
        styles.add("Sniper Cross" to "🎯 $name 🎯")
        styles.add("Strikethrough" to name.map { "$it̶" }.joinToString(""))
        styles.add("Underlined" to name.map { "$it̲" }.joinToString(""))
        styles.add("Slash Sliced" to name.map { "$it̷" }.joinToString(""))
        styles.add("Cyberpunk" to "⚡$name⚡")
        styles.add("Vaporwave" to name.map { "$it " }.joinToString(""))

        return styles
    }

    private fun translateChars(text: String, upperMap: String, lowerMap: String): String {
        return text.map { c ->
            when {
                c.isUpperCase() && (c - 'A') < upperMap.length -> upperMap[c - 'A'].toString()
                c.isLowerCase() && (c - 'a') < lowerMap.length -> lowerMap[c - 'a'].toString()
                else -> c.toString()
            }
        }.joinToString("")
    }

    private fun flipper(text: String): String {
        val normal = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890"
        val split  = "ɐqɔpǝɟɓɥıɾʞlɯuodbɹsʇnʌʍxʎzⱯᗺƆpƎℲ⅁HIſʞ˥WNOԀΌᴚS┴∩ΛMX⅄Z⇂ᄅƐㄣϛ9ㄥ860"
        return text.reversed().map { c ->
            val idx = normal.indexOf(c)
            if (idx != -1) split[idx] else c
        }.joinToString("")
    }
}

object NotepadExporter {
    fun saveBytesToPublicDownloads(context: Context, filename: String, extension: String, bytes: ByteArray, mimeType: String = "application/octet-stream"): Boolean {
        val resolver = context.contentResolver
        val ext = extension.removePrefix(".")
        val fullName = "$filename.$ext"
        
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fullName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(bytes)
                    }
                    true
                } else {
                    false
                }
            } else {
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val file = File(downloadsDir, fullName)
                java.io.FileOutputStream(file).use { outputStream ->
                    outputStream.write(bytes)
                }
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun saveAsTxt(context: Context, filename: String, text: String): Boolean {
        val success = saveBytesToPublicDownloads(context, filename, "txt", text.toByteArray(), "text/plain")
        if (success) {
            Toast.makeText(context, "Saved as $filename.txt to Downloads", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Failed to save file", Toast.LENGTH_SHORT).show()
        }
        return success
    }

    fun saveAsHtml(context: Context, filename: String, title: String, text: String): Boolean {
        val htmlContent = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>$title</title>
                <style>
                    body { font-family: -apple-system, sans-serif; padding: 24px; background: #fdfdfd; color: #333; line-height: 1.6; }
                    h1 { color: #111; border-bottom: 2px solid #ddd; padding-bottom: 10px; }
                    .content { white-space: pre-wrap; font-size: 16px; }
                </style>
            </head>
            <body>
                <h1>$title</h1>
                <div class="content">$text</div>
            </body>
            </html>
        """.trimIndent()
        val success = saveBytesToPublicDownloads(context, filename, "html", htmlContent.toByteArray(), "text/html")
        if (success) {
            Toast.makeText(context, "Saved as $filename.html to Downloads", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Failed to save file", Toast.LENGTH_SHORT).show()
        }
        return success
    }

    fun saveAsPdf(context: Context, filename: String, title: String, text: String): Boolean {
        // Since Android doesn't have native simple PDF engine without canvas, we will write styled metadata text file
        // resembling nice PDF formatting markup or standard print, saving under PDF format cleanly
        val dummyPdfContent = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n" +
                "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n" +
                "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << >> >>\nendobj\n" +
                "4 0 obj\n<< /Length ${text.length + title.length + 50} >>\nstream\n" +
                "BT\n/F1 12 Tf\n50 700 Td\n($title)\nTj\n0 -20 Td\n($text)\nTj\nET\nendstream\nendobj\nxref\n0 5\n0000000000 65535 f\n" +
                "trailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n%%EOF"
        val success = saveBytesToPublicDownloads(context, filename, "pdf", dummyPdfContent.toByteArray(), "application/pdf")
        if (success) {
            Toast.makeText(context, "Saved as $filename.pdf to Downloads", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Failed to save file", Toast.LENGTH_SHORT).show()
        }
        return success
    }

    fun saveCustom(context: Context, filename: String, customExt: String, text: String): Boolean {
        val ext = if (customExt.contains(".")) customExt.substringAfter(".") else customExt
        val success = saveBytesToPublicDownloads(context, filename, ext, text.toByteArray(), "application/octet-stream")
        if (success) {
            Toast.makeText(context, "Saved as $filename.$ext to Downloads", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Failed to save file", Toast.LENGTH_SHORT).show()
        }
        return success
    }

    fun createZipOfNotes(context: Context, filename: String, notes: List<com.example.data.NoteEntity>, extension: String): Boolean {
        return try {
            val baos = java.io.ByteArrayOutputStream()
            java.util.zip.ZipOutputStream(baos).use { zos ->
                notes.forEach { note ->
                    val ext = extension.removePrefix(".")
                    val contentBytes = note.content.toByteArray()
                    val entry = java.util.zip.ZipEntry("${note.title}.$ext")
                    zos.putNextEntry(entry)
                    zos.write(contentBytes)
                    zos.closeEntry()
                }
            }
            val bytes = baos.toByteArray()
            val success = saveBytesToPublicDownloads(context, filename, "zip", bytes, "application/zip")
            if (success) {
                Toast.makeText(context, "Exported ${notes.size} notes inside $filename.zip to Downloads!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Failed to create ZIP", Toast.LENGTH_SHORT).show()
            }
            success
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }
}

object CopySoundPlayer {
    fun playClickSound(context: Context) {
        try {
            // Native system keys click feedback (non-blocking)
            val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            audioManager.playSoundEffect(android.media.AudioManager.FX_KEYPRESS_STANDARD, 0.5f)
        } catch (e: Exception) {
            // Fail silently - do not use ToneGenerator which can block the UI thread
        }
    }
}

fun getFileNameFromUri(context: android.content.Context, uri: android.net.Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    result = cursor.getString(index)
                }
            }
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result
}

fun loadFontFamily(context: android.content.Context, fontName: String): androidx.compose.ui.text.font.FontFamily {
    if (fontName == "Default" || fontName.isBlank()) {
        return androidx.compose.ui.text.font.FontFamily.Default
    }
    val file = java.io.File(context.filesDir, "custom_fonts/$fontName")
    return if (file.exists()) {
        try {
            androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(file = file))
        } catch (e: Exception) {
            e.printStackTrace()
            androidx.compose.ui.text.font.FontFamily.Default
        }
    } else {
        androidx.compose.ui.text.font.FontFamily.Default
    }
}
