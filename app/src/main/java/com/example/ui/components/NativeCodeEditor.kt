package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.regex.Pattern

/** Colours used by the native code editor. */
class EditorColors(
    val background: Color,
    val text: Color,
    val gutterBackground: Color,
    val gutterText: Color,
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
    val tag: Color,
    val cursor: Color
)

fun editorColorsFor(theme: String): EditorColors = when (theme.lowercase()) {
    "light" -> EditorColors(
        background = Color(0xFFFFFFFF), text = Color(0xFF24292F),
        gutterBackground = Color(0xFFF6F8FA), gutterText = Color(0xFF8C959F),
        keyword = Color(0xFFCF222E), string = Color(0xFF0A3069), comment = Color(0xFF6E7781),
        number = Color(0xFF0550AE), tag = Color(0xFF116329), cursor = Color(0xFF0969DA)
    )
    "dracula" -> EditorColors(
        background = Color(0xFF282A36), text = Color(0xFFF8F8F2),
        gutterBackground = Color(0xFF282A36), gutterText = Color(0xFF6272A4),
        keyword = Color(0xFFFF79C6), string = Color(0xFFF1FA8C), comment = Color(0xFF6272A4),
        number = Color(0xFFBD93F9), tag = Color(0xFF50FA7B), cursor = Color(0xFFF8F8F2)
    )
    else -> EditorColors(
        background = Color(0xFF0D1117), text = Color(0xFFC9D1D9),
        gutterBackground = Color(0xFF0D1117), gutterText = Color(0xFF6E7681),
        keyword = Color(0xFFFF7B72), string = Color(0xFFA5D6FF), comment = Color(0xFF8B949E),
        number = Color(0xFF79C0FF), tag = Color(0xFF7EE787), cursor = Color(0xFF58A6FF)
    )
}

private const val KW_KOTLIN = "as break class continue do else false for fun if in interface is null object package " +
    "return super this throw true try typealias val var when while import override private public protected " +
    "internal data sealed open abstract lateinit by companion const suspend inline enum annotation init constructor"
private const val KW_JAVA = "abstract assert boolean break byte case catch char class const continue default do double " +
    "else enum extends final finally float for goto if implements import instanceof int interface long native new " +
    "null package private protected public return short static super switch synchronized this throw throws " +
    "transient true false try void volatile while var record"
private const val KW_JS = "async await break case catch class const continue debugger default delete do else export " +
    "extends false finally for function if import in instanceof let new null of return static super switch this " +
    "throw true try typeof undefined var void while with yield"
private const val KW_PY = "and as assert async await break class continue def del elif else except False finally for " +
    "from global if import in is lambda None nonlocal not or pass raise return True try while with yield"
private const val KW_PHP = "abstract and array as break case catch class clone const continue declare default do echo " +
    "else elseif empty extends false final finally for foreach function global if implements include interface " +
    "isset namespace new null or print private protected public require return static switch this throw true " +
    "try use var while"
private const val KW_SQL = "select from where insert into values update set delete create table drop alter add join " +
    "left right inner outer on group by order having limit as and or not null primary key foreign references " +
    "index distinct union all case when then else end like in is between exists"
private const val KW_JSON = "true false null"
private const val KW_YAML = "true false null yes no on off"

private val patternCache = HashMap<String, Pattern?>()

private fun slashComments() = """//[^\n]*|/\*[\s\S]*?\*/"""
private fun hashComments() = """#[^\n]*"""
private const val NEVER = "(?!)"
private const val STRINGS = "\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'"
private const val XML_STRINGS = "\"[^\"\\n]*\"|'[^'\\n]*'"
private const val NUMBERS = """\b\d+(?:\.\d+)?\b"""

private fun kw(words: String, ignoreCase: Boolean = false): String {
    val flag = if (ignoreCase) "(?i)" else ""
    return flag + """\b(?:""" + words.trim().split(" ").joinToString("|") + """)\b"""
}

private fun build(comment: String, string: String, tag: String, number: String, keywords: String, flags: Int = 0): Pattern =
    Pattern.compile("($comment)|($string)|($tag)|($number)|($keywords)", flags)

private fun patternFor(language: String): Pattern? = synchronized(patternCache) {
    patternCache.getOrPut(language) {
        try {
            when (language) {
                "kotlin" -> build(slashComments(), STRINGS, NEVER, NUMBERS, kw(KW_KOTLIN))
                "groovy" -> build(slashComments(), STRINGS, NEVER, NUMBERS, kw(KW_JAVA))
                "java" -> build(slashComments(), STRINGS, NEVER, NUMBERS, kw(KW_JAVA))
                "javascript" -> build(slashComments(), STRINGS + """|`[^`]*`""", NEVER, NUMBERS, kw(KW_JS))
                "css" -> build("""/\*[\s\S]*?\*/""", STRINGS, """[A-Za-z-]+(?=\s*:)""", """#[0-9a-fA-F]{3,8}\b|\b\d+(?:\.\d+)?(?:px|em|rem|%|vh|vw|s|ms)?\b""", NEVER)
                "python" -> build(hashComments(), STRINGS, NEVER, NUMBERS, kw(KW_PY))
                "php" -> build(slashComments() + "|" + hashComments(), STRINGS, NEVER, NUMBERS, kw(KW_PHP))
                "sql" -> build("""--[^\n]*|/\*[\s\S]*?\*/""", """'(?:''|[^'])*'""", NEVER, NUMBERS, kw(KW_SQL, true))
                "json" -> build(NEVER, STRINGS, NEVER, """-?\b\d+(?:\.\d+)?(?:[eE][+-]?\d+)?\b""", kw(KW_JSON))
                "yaml" -> build(hashComments(), STRINGS, """^\s*[A-Za-z_][\w.-]*(?=\s*:)""", NUMBERS, kw(KW_YAML), Pattern.MULTILINE)
                "xml", "html" -> build("""<!--[\s\S]*?-->""", XML_STRINGS, """</?[A-Za-z][\w:.\-]*|/?>""", NEVER, NEVER)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}

/** Builds a syntax-highlighted AnnotatedString. Falls back to plain text for huge files. */
fun highlightCode(text: String, language: String, colors: EditorColors): AnnotatedString {
    val pattern = patternFor(language)
    if (pattern == null || text.length > 150_000) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        try {
            val m = pattern.matcher(text)
            while (m.find()) {
                if (m.end() <= m.start()) continue
                val color = when {
                    m.start(1) >= 0 -> colors.comment
                    m.start(2) >= 0 -> colors.string
                    m.start(3) >= 0 -> colors.tag
                    m.start(4) >= 0 -> colors.number
                    else -> colors.keyword
                }
                addStyle(SpanStyle(color = color), m.start(), m.end())
            }
        } catch (e: Exception) {
            // highlighting is cosmetic only; never break editing
        }
    }
}

private class SyntaxTransformation(
    private val language: String,
    private val colors: EditorColors
) : VisualTransformation {
    private var lastText: String? = null
    private var lastResult: AnnotatedString? = null

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val cached = lastResult
        val result = if (cached != null && raw == lastText) {
            cached
        } else {
            highlightCode(raw, language, colors).also {
                lastText = raw
                lastResult = it
            }
        }
        return TransformedText(result, OffsetMapping.Identity)
    }
}

/**
 * A pure-Compose code editor (no WebView): line numbers, syntax colours, horizontal + vertical scrolling.
 */
@Composable
fun NativeCodeEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    language: String,
    theme: String,
    fontSize: Int,
    wordWrap: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = remember(theme) { editorColorsFor(theme) }
    val transformation = remember(language, theme) { SyntaxTransformation(language, colors) }
    val textStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.5f).sp,
        color = colors.text
    )
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    val lineCount = remember(value.text) { value.text.count { it == '\n' } + 1 }
    val lineNumbers = remember(lineCount) { (1..lineCount).joinToString("\n") }

    BoxWithConstraints(modifier = modifier.background(colors.background)) {
        val viewportWidth = maxWidth
        val viewportHeight = maxHeight

        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(vScroll)
        ) {
            if (!wordWrap) {
                Text(
                    text = lineNumbers,
                    style = textStyle.copy(color = colors.gutterText, textAlign = TextAlign.End),
                    softWrap = false,
                    modifier = Modifier
                        .background(colors.gutterBackground)
                        .heightIn(min = viewportHeight)
                        .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 120.dp)
                )
            }

            val boxModifier = if (wordWrap) {
                Modifier.weight(1f)
            } else {
                Modifier
                    .weight(1f)
                    .horizontalScroll(hScroll)
            }

            Box(modifier = boxModifier) {
                val fieldModifier = if (wordWrap) {
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = viewportHeight)
                } else {
                    Modifier
                        .widthIn(min = (viewportWidth - 48.dp).coerceAtLeast(0.dp))
                        .heightIn(min = viewportHeight)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = textStyle,
                    cursorBrush = SolidColor(colors.cursor),
                    visualTransformation = transformation,
                    modifier = fieldModifier,
                    decorationBox = { inner ->
                        Box(modifier = Modifier.padding(start = 8.dp, end = 24.dp, top = 8.dp, bottom = 120.dp)) {
                            inner()
                        }
                    }
                )
            }
        }
    }
}
