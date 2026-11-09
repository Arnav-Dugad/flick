package io.github.arnavdugad.flick.ui.screens.update

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Renders the small subset of Markdown used in release notes: `#` headings, `-`/`*` bullets,
 * `**bold**` and `` `code` ``. Links show as their text. Good enough without a library.
 */
object MarkdownLite {

    fun render(markdown: String, headingStyle: SpanStyle): AnnotatedString = buildAnnotatedString {
        val lines = markdown.replace("\r\n", "\n").lines()
        var first = true
        for (raw in lines) {
            val line = raw.trimEnd()
            if (line.isBlank()) continue
            if (!first) append('\n')
            first = false
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("#") -> withStyle(headingStyle) { inline(trimmed.trimStart('#').trim()) }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    append("•  ")
                    inline(trimmed.substring(2))
                }
                else -> inline(trimmed)
            }
        }
    }

    private val LINK = Regex("""\[([^\]]+)]\([^)]+\)""")

    private fun AnnotatedString.Builder.inline(text: String) {
        val plain = LINK.replace(text) { it.groupValues[1] }
        var i = 0
        while (i < plain.length) {
            when {
                plain.startsWith("**", i) -> {
                    val end = plain.indexOf("**", i + 2)
                    if (end < 0) { append(plain.substring(i)); return }
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(plain.substring(i + 2, end)) }
                    i = end + 2
                }
                plain[i] == '`' -> {
                    val end = plain.indexOf('`', i + 1)
                    if (end < 0) { append(plain.substring(i)); return }
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(plain.substring(i + 1, end)) }
                    i = end + 1
                }
                else -> {
                    append(plain[i])
                    i++
                }
            }
        }
    }
}
