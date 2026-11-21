package com.example.mediminder.ui.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.mediminder.R
import com.example.mediminder.ui.components.AppTopBar
import com.example.mediminder.util.Formats

/** Which page: the privacy policy or the terms of use. */
enum class LegalDoc(val file: String) { PRIVACY("privacy"), TERMS("terms") }

/**
 * The privacy policy or terms, in the app's language. The same files are published on
 * GitHub (docs/legal; the Bangla ones only in the app), so the app and the web say the same thing.
 */
@Composable
fun LegalScreen(doc: LegalDoc, onBack: () -> Unit) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val lang = if (Formats.isBangla(context)) "bn" else "en"
    val lines = remember(doc, lang) {
        runCatching { context.assets.open("${doc.file}.$lang.md").bufferedReader().use { it.readLines() } }.getOrDefault(emptyList())
    }
    val linkColor = MaterialTheme.colorScheme.primary
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = stringResource(if (doc == LegalDoc.PRIVACY) R.string.legal_privacy else R.string.legal_terms), onBack = onBack) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            lines.forEach { raw ->
                val line = raw.trim()
                val link = Regex("""https?://\S+""").find(line)?.value
                val clickable = if (link != null) Modifier.clickable { uri.openUri(link) } else Modifier
                when {
                    line.isEmpty() -> Spacer(Modifier.padding(2.dp))
                    line.startsWith("# ") -> Text(line.removePrefix("# "), style = MaterialTheme.typography.headlineSmall)
                    line.startsWith("## ") -> Text(line.removePrefix("## "), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
                    line.startsWith("- ") -> Row(clickable) {
                        Text("•", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.width(10.dp))
                        Text(styled(line.removePrefix("- "), link, linkColor), style = MaterialTheme.typography.bodyLarge)
                    }
                    else -> Text(styled(line, link, linkColor), style = MaterialTheme.typography.bodyLarge, modifier = clickable)
                }
            }
        }
    }
}

/** **bold** parts in bold, and a link underlined. */
private fun styled(text: String, link: String?, linkColor: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
    text.split("**").forEachIndexed { i, part ->
        val write: () -> Unit = {
            if (link != null && link in part) {
                val at = part.indexOf(link)
                append(part.substring(0, at))
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) { append(link) }
                append(part.substring(at + link.length))
            } else append(part)
        }
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { write() } else write()
    }
}
