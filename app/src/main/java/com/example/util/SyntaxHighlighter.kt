package com.example.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit

object SyntaxHighlighter {
    // Beautiful IDE Theme Colors
    private val keywordColor = Color(0xFFFF79C6) // Hot Pink for fun, class, def, html
    private val typeColor = Color(0xFF8BE9FD)    // Cyan for String, Int, void
    private val valColor = Color(0xFF50FA7B)     // Light Green for variables/vals
    private val commentColor = Color(0xFF6272A4) // Gray comments
    private val stringColor = Color(0xFFF1FA8C)  // Yellow strings
    private val numberColor = Color(0xFFBD93F9)  // Lavender numbers
    private val tagColor = Color(0xFFFF5555)     // Red for brackets/tags

    private val keywords = setOf(
        "fun", "class", "package", "import", "val", "var", "def", "public", "private", "protected",
        "return", "if", "else", "for", "while", "do", "break", "continue", "interface", "object",
        "override", "super", "this", "try", "catch", "finally", "throw", "null", "true", "false",
        "html", "body", "div", "span", "script", "style", "h1", "h2", "h3", "p", "a", "link",
        "def", "import", "as", "from", "lambda", "elif", "print"
    )

    private val types = setOf(
        "String", "Int", "Boolean", "Float", "Double", "Long", "Char", "Byte", "Short", "Unit",
        "Void", "void", "List", "Map", "Set", "Array", "var", "const", "let"
    )

    fun highlightCode(code: String, enabled: Boolean): AnnotatedString {
        if (!enabled) {
            return AnnotatedString(code)
        }

        return buildAnnotatedString {
            var index = 0
            val length = code.length

            while (index < length) {
                // 1. Handle single-line comments
                if (index + 1 < length && code[index] == '/' && code[index + 1] == '/') {
                    val endOfLine = code.indexOf('\n', index)
                    val commentEnd = if (endOfLine != -1) endOfLine else length
                    pushStyle(SpanStyle(color = commentColor, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                    append(code.substring(index, commentEnd))
                    pop()
                    index = commentEnd
                    continue
                }

                // 2. Handle strings (double quotes)
                if (code[index] == '"') {
                    val nextQuote = code.indexOf('"', index + 1)
                    val stringEnd = if (nextQuote != -1) nextQuote + 1 else length
                    pushStyle(SpanStyle(color = stringColor))
                    append(code.substring(index, stringEnd))
                    pop()
                    index = stringEnd
                    continue
                }

                // 3. Handle strings (single quotes)
                if (code[index] == '\'') {
                    val nextQuote = code.indexOf('\'', index + 1)
                    val stringEnd = if (nextQuote != -1) nextQuote + 1 else length
                    pushStyle(SpanStyle(color = stringColor))
                    append(code.substring(index, stringEnd))
                    pop()
                    index = stringEnd
                    continue
                }

                // 4. Handle HTML/XML elements etc or tags
                if (code[index] == '<' || code[index] == '>') {
                    pushStyle(SpanStyle(color = tagColor, fontWeight = FontWeight.Bold))
                    append(code[index].toString())
                    pop()
                    index++
                    continue
                }

                // 5. Handle numbers
                if (code[index].isDigit()) {
                    var endDigit = index
                    while (endDigit < length && (code[endDigit].isDigit() || code[endDigit] == '.')) {
                        endDigit++
                    }
                    pushStyle(SpanStyle(color = numberColor))
                    append(code.substring(index, endDigit))
                    pop()
                    index = endDigit
                    continue
                }

                // 6. Handle alphabetic words
                if (code[index].isLetter() || code[index] == '_') {
                    var endWord = index
                    while (endWord < length && (code[endWord].isLetterOrDigit() || code[endWord] == '_')) {
                        endWord++
                    }
                    val word = code.substring(index, endWord)
                    when {
                        keywords.contains(word) -> {
                            pushStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold))
                            append(word)
                            pop()
                        }
                        types.contains(word) -> {
                            pushStyle(SpanStyle(color = typeColor))
                            append(word)
                            pop()
                        }
                        word.uppercase() == word && word.length > 2 -> {
                            // Constant or uppercase
                            pushStyle(SpanStyle(color = valColor, fontWeight = FontWeight.SemiBold))
                            append(word)
                            pop()
                        }
                        else -> {
                            append(word)
                        }
                    }
                    index = endWord
                    continue
                }

                // Default char append
                append(code[index].toString())
                index++
            }
        }
    }

    fun highlightRichText(text: String, enabled: Boolean = true): AnnotatedString {
        if (!enabled || text.length > 10000) {
            return AnnotatedString(text)
        }
        return buildAnnotatedString {
            var index = 0
            val length = text.length

            var insideBold = 0
            var insideItalic = 0
            var insideUnderline = 0
            var insideStrike = 0
            var insideGlow = 0
            var currentColor: Color? = null
            var currentFontSize: androidx.compose.ui.unit.TextUnit? = null

            val colorStack = java.util.Stack<Color>()
            val sizeStack = java.util.Stack<androidx.compose.ui.unit.TextUnit>()

            val tagStyle = SpanStyle(
                color = Color(0x2064748B),
                fontSize = androidx.compose.ui.unit.TextUnit.Unspecified,
                fontWeight = FontWeight.Normal
            )

            while (index < length) {
                // 1. Color tag <color hex="#1E293B">
                if (index + 12 <= length && text.substring(index, index + 11) == "<color hex=\"") {
                    val closeIdx = text.indexOf("\">", index)
                    if (closeIdx != -1) {
                        val hexStr = text.substring(index + 12, closeIdx)
                        try {
                            val parsed = Color(android.graphics.Color.parseColor(hexStr))
                            colorStack.push(parsed)
                            currentColor = parsed
                        } catch (e: Exception) {}
                        pushStyle(tagStyle)
                        append(text.substring(index, closeIdx + 2))
                        pop()
                        index = closeIdx + 2
                        continue
                    }
                }
                if (index + 8 <= length && text.substring(index, index + 8) == "</color>") {
                    if (!colorStack.isEmpty()) colorStack.pop()
                    currentColor = if (!colorStack.isEmpty()) colorStack.peek() else null
                    pushStyle(tagStyle)
                    append("</color>")
                    pop()
                    index += 8
                    continue
                }

                // 2. Size tag <size sp="18">
                if (index + 10 <= length && text.substring(index, index + 10) == "<size sp=\"") {
                    val closeIdx = text.indexOf("\">", index)
                    if (closeIdx != -1) {
                        val spStr = text.substring(index + 10, closeIdx)
                        try {
                            val parsedSp = spStr.toInt().sp
                            sizeStack.push(parsedSp)
                            currentFontSize = parsedSp
                        } catch (e: Exception) {}
                        pushStyle(tagStyle)
                        append(text.substring(index, closeIdx + 2))
                        pop()
                        index = closeIdx + 2
                        continue
                    }
                }
                if (index + 7 <= length && text.substring(index, index + 7) == "</size>") {
                    if (!sizeStack.isEmpty()) sizeStack.pop()
                    currentFontSize = if (!sizeStack.isEmpty()) sizeStack.peek() else null
                    pushStyle(tagStyle)
                    append("</size>")
                    pop()
                    index += 7
                    continue
                }

                // 3. <b>, </b>
                if (index + 3 <= length && text.substring(index, index + 3) == "<b>") {
                    pushStyle(tagStyle)
                    append("<b>")
                    pop()
                    insideBold++
                    index += 3
                    continue
                }
                if (index + 4 <= length && text.substring(index, index + 4) == "</b>") {
                    pushStyle(tagStyle)
                    append("</b>")
                    pop()
                    if (insideBold > 0) insideBold--
                    index += 4
                    continue
                }

                // 4. <i>, </i>
                if (index + 3 <= length && text.substring(index, index + 3) == "<i>") {
                    pushStyle(tagStyle)
                    append("<i>")
                    pop()
                    insideItalic++
                    index += 3
                    continue
                }
                if (index + 4 <= length && text.substring(index, index + 4) == "</i>") {
                    pushStyle(tagStyle)
                    append("</i>")
                    pop()
                    if (insideItalic > 0) insideItalic--
                    index += 4
                    continue
                }

                // 5. <u>, </u>
                if (index + 3 <= length && text.substring(index, index + 3) == "<u>") {
                    pushStyle(tagStyle)
                    append("<u>")
                    pop()
                    insideUnderline++
                    index += 3
                    continue
                }
                if (index + 4 <= length && text.substring(index, index + 4) == "</u>") {
                    pushStyle(tagStyle)
                    append("</u>")
                    pop()
                    if (insideUnderline > 0) insideUnderline--
                    index += 4
                    continue
                }

                // 6. <s>, </s> (strikethrough)
                if (index + 3 <= length && text.substring(index, index + 3) == "<s>") {
                    pushStyle(tagStyle)
                    append("<s>")
                    pop()
                    insideStrike++
                    index += 3
                    continue
                }
                if (index + 4 <= length && text.substring(index, index + 4) == "</s>") {
                    pushStyle(tagStyle)
                    append("</s>")
                    pop()
                    if (insideStrike > 0) insideStrike--
                    index += 4
                    continue
                }

                // 7. <g>, </g>
                if (index + 3 <= length && text.substring(index, index + 3) == "<g>") {
                    pushStyle(tagStyle)
                    append("<g>")
                    pop()
                    insideGlow++
                    index += 3
                    continue
                }
                if (index + 4 <= length && text.substring(index, index + 4) == "</g>") {
                    pushStyle(tagStyle)
                    append("</g>")
                    pop()
                    if (insideGlow > 0) insideGlow--
                    index += 4
                    continue
                }

                val charStr = text[index].toString()
                val decorations = mutableListOf<androidx.compose.ui.text.style.TextDecoration>()
                if (insideUnderline > 0) decorations.add(androidx.compose.ui.text.style.TextDecoration.Underline)
                if (insideStrike > 0) decorations.add(androidx.compose.ui.text.style.TextDecoration.LineThrough)

                val combinedDecoration = when {
                    decorations.contains(androidx.compose.ui.text.style.TextDecoration.Underline) && decorations.contains(androidx.compose.ui.text.style.TextDecoration.LineThrough) ->
                        androidx.compose.ui.text.style.TextDecoration.combine(decorations)
                    decorations.contains(androidx.compose.ui.text.style.TextDecoration.Underline) -> androidx.compose.ui.text.style.TextDecoration.Underline
                    decorations.contains(androidx.compose.ui.text.style.TextDecoration.LineThrough) -> androidx.compose.ui.text.style.TextDecoration.LineThrough
                    else -> null
                }

                val style = SpanStyle(
                    fontWeight = if (insideBold > 0) FontWeight.Bold else null,
                    fontStyle = if (insideItalic > 0) androidx.compose.ui.text.font.FontStyle.Italic else null,
                    textDecoration = combinedDecoration,
                    color = when {
                        insideGlow > 0 -> Color(0xFFEC4899)
                        currentColor != null -> currentColor
                        else -> Color.Unspecified
                    },
                    fontSize = currentFontSize ?: androidx.compose.ui.unit.TextUnit.Unspecified,
                    shadow = if (insideGlow > 0) androidx.compose.ui.graphics.Shadow(
                        color = Color(0xFFEC4899).copy(alpha = 0.9f),
                        blurRadius = 16f
                    ) else null
                )
                pushStyle(style)
                append(charStr)
                pop()
                index++
            }
        }
    }
}
