package com.example.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.util.regex.Pattern

/**
 * Nexa IDE Syntax Highlighter
 *
 * Real-time Jetpack Compose VisualTransformation supporting 15+ programming & markup languages:
 * 1. Kotlin (.kt)
 * 2. Java (.java)
 * 3. XML (.xml)
 * 4. JSON (.json)
 * 5. HTML (.html, .htm)
 * 6. CSS (.css, .scss)
 * 7. JavaScript (.js, .mjs, .cjs)
 * 8. TypeScript (.ts, .tsx)
 * 9. Gradle (.gradle, .gradle.kts)
 * 10. Markdown (.md)
 * 11. YAML (.yml, .yaml)
 * 12. PHP (.php)
 * 13. Python (.py)
 * 14. SQL (.sql)
 * 15. Shell / Bash (.sh, .bash)
 */
class CodeSyntaxHighlighter(
    private val extension: String,
    private val theme: String = "dark"
) : VisualTransformation {

    // Palette calibrated for high readability and premium developer aesthetic
    private val colorKeyword = if (theme == "light") Color(0xFF9C27B0) else Color(0xFFFF79C6) // Pink / Purple
    private val colorString = if (theme == "light") Color(0xFF2E7D32) else Color(0xFF50FA7B) // Green
    private val colorNumber = if (theme == "light") Color(0xFFE65100) else Color(0xFFFFB86C) // Orange
    private val colorComment = if (theme == "light") Color(0xFF757575) else Color(0xFF6272A4) // Slate Muted Gray
    private val colorTypeOrTag = if (theme == "light") Color(0xFF0077AA) else Color(0xFF8BE9FD) // Cyan
    private val colorAnnotationOrAttr = if (theme == "light") Color(0xFFD84315) else Color(0xFFF1FA8C) // Yellow / Gold
    private val colorConstant = if (theme == "light") Color(0xFF673AB7) else Color(0xFFBD93F9) // Light Purple

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val builder = AnnotatedString.Builder(raw)
        val ext = extension.lowercase()

        try {
            when {
                ext in listOf("kt", "kts", "kotlin") -> highlightKotlin(raw, builder)
                ext in listOf("java") -> highlightJava(raw, builder)
                ext in listOf("xml", "svg", "plist") -> highlightXml(raw, builder)
                ext in listOf("html", "htm") -> highlightHtml(raw, builder)
                ext in listOf("json") -> highlightJson(raw, builder)
                ext in listOf("css", "scss", "sass") -> highlightCss(raw, builder)
                ext in listOf("js", "mjs", "cjs", "jsx") -> highlightJavaScript(raw, builder)
                ext in listOf("ts", "tsx") -> highlightTypeScript(raw, builder)
                ext in listOf("gradle") -> highlightGradle(raw, builder)
                ext in listOf("yml", "yaml") -> highlightYaml(raw, builder)
                ext in listOf("py", "pyw") -> highlightPython(raw, builder)
                ext in listOf("php") -> highlightPhp(raw, builder)
                ext in listOf("sql") -> highlightSql(raw, builder)
                ext in listOf("sh", "bash", "zsh") -> highlightShell(raw, builder)
                ext in listOf("md", "markdown") -> highlightMarkdown(raw, builder)
                else -> highlightGeneric(raw, builder)
            }
        } catch (e: Exception) {
            // Highlighting error should never crash the editor
        }

        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }

    private fun highlightKotlin(code: String, builder: AnnotatedString.Builder) {
        val keywords = "\\b(package|import|fun|class|interface|object|val|var|data|sealed|enum|override|open|abstract|private|protected|public|internal|lateinit|companion|if|else|when|for|while|do|return|break|continue|try|catch|finally|throw|is|in|as|by|lazy|suspend|coroutine|inline|crossinline|noinline|reified|typealias)\\b"
        val types = "\\b(String|Int|Long|Double|Float|Boolean|Byte|Short|Char|Unit|Any|List|Map|Set|Array|MutableList|MutableMap|Flow|StateFlow|CoroutineScope|Modifier|Color|Context|View|Bundle)\\b"
        val constants = "\\b(true|false|null|this|super)\\b"

        applyRegex(code, builder, keywords, colorKeyword, fontWeight = FontWeight.Bold)
        applyRegex(code, builder, types, colorTypeOrTag)
        applyRegex(code, builder, constants, colorConstant)
        applyRegex(code, builder, "@[A-Za-z0-9_]+", colorAnnotationOrAttr)
        applyRegex(code, builder, "\\b[0-9]+(\\.[0-9]+)?([fFL]?)\\b", colorNumber)
        applyRegex(code, builder, "\"(\\\\.|[^\"])*\"|'(\\\\.|[^'])*'", colorString)
        applyRegex(code, builder, "//.*|/\\*[\\s\\S]*?\\*/", colorComment)
    }

    private fun highlightJava(code: String, builder: AnnotatedString.Builder) {
        val keywords = "\\b(package|import|public|private|protected|static|final|abstract|class|interface|extends|implements|void|int|long|double|float|boolean|char|byte|short|new|return|if|else|switch|case|default|break|continue|for|while|do|try|catch|finally|throw|throws|instanceof|synchronized|volatile|transient|native)\\b"
        val constants = "\\b(true|false|null|this|super)\\b"

        applyRegex(code, builder, keywords, colorKeyword, fontWeight = FontWeight.Bold)
        applyRegex(code, builder, constants, colorConstant)
        applyRegex(code, builder, "@[A-Za-z0-9_]+", colorAnnotationOrAttr)
        applyRegex(code, builder, "\\b[0-9]+(\\.[0-9]+)?([fFLdD]?)\\b", colorNumber)
        applyRegex(code, builder, "\"(\\\\.|[^\"])*\"|'(\\\\.|[^'])*'", colorString)
        applyRegex(code, builder, "//.*|/\\*[\\s\\S]*?\\*/", colorComment)
    }

    private fun highlightXml(code: String, builder: AnnotatedString.Builder) {
        // Tag names
        applyRegex(code, builder, "</?[a-zA-Z0-9_.:-]+", colorTypeOrTag, fontWeight = FontWeight.Bold)
        // Attributes
        applyRegex(code, builder, "\\s[a-zA-Z0-9_.:-]+(?=\\=)", colorAnnotationOrAttr)
        // Attribute values (strings)
        applyRegex(code, builder, "\"(\\\\.|[^\"])*\"|'(\\\\.|[^'])*'", colorString)
        // XML Comments
        applyRegex(code, builder, "<!--[\\s\\S]*?-->", colorComment)
    }

    private fun highlightHtml(code: String, builder: AnnotatedString.Builder) {
        // Doctype
        applyRegex(code, builder, "<!DOCTYPE[^>]*>", colorKeyword, fontWeight = FontWeight.Bold)
        // HTML Tags
        applyRegex(code, builder, "</?[a-zA-Z0-9-]+", colorTypeOrTag, fontWeight = FontWeight.Bold)
        // Attributes
        applyRegex(code, builder, "\\s[a-zA-Z0-9-]+(?=\\=)", colorAnnotationOrAttr)
        // Strings
        applyRegex(code, builder, "\"(\\\\.|[^\"])*\"|'(\\\\.|[^'])*'", colorString)
        // HTML Comments
        applyRegex(code, builder, "<!--[\\s\\S]*?-->", colorComment)
    }

    private fun highlightJson(code: String, builder: AnnotatedString.Builder) {
        // Keys
        applyRegex(code, builder, "\"([^\"]+)\"\\s*:", colorTypeOrTag)
        // String values
        applyRegex(code, builder, ":\\s*\"(\\\\.|[^\"])*\"", colorString)
        // Numbers
        applyRegex(code, builder, "\\b-?[0-9]+(\\.[0-9]+)?([eE][+-]?[0-9]+)?\\b", colorNumber)
        // Booleans & Null
        applyRegex(code, builder, "\\b(true|false|null)\\b", colorConstant, fontWeight = FontWeight.Bold)
    }

    private fun highlightCss(code: String, builder: AnnotatedString.Builder) {
        // Selectors & Classes
        applyRegex(code, builder, "[.#]?[a-zA-Z0-9_-]+(?=\\s*\\{)", colorKeyword, fontWeight = FontWeight.Bold)
        // CSS Properties
        applyRegex(code, builder, "[a-zA-Z0-9_-]+(?=\\s*:)", colorTypeOrTag)
        // Values & units
        applyRegex(code, builder, ":\\s*[^;{}]+", colorString)
        // Numbers with units (px, rem, em, %, vh, etc.)
        applyRegex(code, builder, "\\b[0-9]+(\\.[0-9]+)?(px|rem|em|%|vh|vw|pt|s|ms)?\\b", colorNumber)
        // Comments
        applyRegex(code, builder, "/\\*[\\s\\S]*?\\*/", colorComment)
    }

    private fun highlightJavaScript(code: String, builder: AnnotatedString.Builder) {
        val keywords = "\\b(const|let|var|function|return|if|else|for|while|do|switch|case|break|continue|try|catch|finally|throw|class|extends|new|import|from|export|default|async|await|yield|typeof|instanceof|void|delete|in|of)\\b"
        val constants = "\\b(true|false|null|undefined|NaN|Infinity|this)\\b"

        applyRegex(code, builder, keywords, colorKeyword, fontWeight = FontWeight.Bold)
        applyRegex(code, builder, constants, colorConstant)
        applyRegex(code, builder, "\\b[0-9]+(\\.[0-9]+)?\\b", colorNumber)
        applyRegex(code, builder, "\"(\\\\.|[^\"])*\"|'(\\\\.|[^'])*'|`(\\\\.|[^`])*`", colorString)
        applyRegex(code, builder, "//.*|/\\*[\\s\\S]*?\\*/", colorComment)
    }

    private fun highlightTypeScript(code: String, builder: AnnotatedString.Builder) {
        val keywords = "\\b(const|let|var|function|return|if|else|for|while|do|switch|case|break|continue|try|catch|finally|throw|class|interface|type|enum|extends|implements|new|import|from|export|default|async|await|yield|typeof|instanceof|as|is|keyof|readonly|declare|namespace)\\b"
        val types = "\\b(string|number|boolean|any|void|unknown|never|object|symbol|bigint|Promise|Array|Record)\\b"
        val constants = "\\b(true|false|null|undefined|NaN|this)\\b"

        applyRegex(code, builder, keywords, colorKeyword, fontWeight = FontWeight.Bold)
        applyRegex(code, builder, types, colorTypeOrTag)
        applyRegex(code, builder, constants, colorConstant)
        applyRegex(code, builder, "\\b[0-9]+(\\.[0-9]+)?\\b", colorNumber)
        applyRegex(code, builder, "\"(\\\\.|[^\"])*\"|'(\\\\.|[^'])*'|`(\\\\.|[^`])*`", colorString)
        applyRegex(code, builder, "//.*|/\\*[\\s\\S]*?\\*/", colorComment)
    }

    private fun highlightGradle(code: String, builder: AnnotatedString.Builder) {
        val keywords = "\\b(plugins|dependencies|implementation|testImplementation|androidTestImplementation|debugImplementation|releaseImplementation|ksp|alias|id|version|android|namespace|compileSdk|minSdk|targetSdk|versionCode|versionName|buildTypes|release|debug|sourceCompatibility|targetCompatibility|buildFeatures|compose|testOptions)\\b"
        applyRegex(code, builder, keywords, colorKeyword, fontWeight = FontWeight.Bold)
        applyRegex(code, builder, "\\b[0-9]+(\\.[0-9]+)?\\b", colorNumber)
        applyRegex(code, builder, "\"(\\\\.|[^\"])*\"|'(\\\\.|[^'])*'", colorString)
        applyRegex(code, builder, "//.*|/\\*[\\s\\S]*?\\*/", colorComment)
    }

    private fun highlightYaml(code: String, builder: AnnotatedString.Builder) {
        // Keys
        applyRegex(code, builder, "^\\s*([a-zA-Z0-9_.-]+)\\s*:", colorTypeOrTag, Pattern.MULTILINE)
        // Strings
        applyRegex(code, builder, "\"(\\\\.|[^\"])*\"|'(\\\\.|[^'])*'", colorString)
        // Numbers
        applyRegex(code, builder, "\\b[0-9]+(\\.[0-9]+)?\\b", colorNumber)
        // Booleans
        applyRegex(code, builder, "\\b(true|false|yes|no|on|off)\\b", colorConstant)
        // Comments
        applyRegex(code, builder, "#.*", colorComment)
    }

    private fun highlightPython(code: String, builder: AnnotatedString.Builder) {
        val keywords = "\\b(def|class|if|elif|else|for|while|return|yield|import|from|as|try|except|finally|raise|with|pass|break|continue|lambda|global|nonlocal|assert|async|await)\\b"
        val constants = "\\b(True|False|None|self)\\b"

        applyRegex(code, builder, keywords, colorKeyword, fontWeight = FontWeight.Bold)
        applyRegex(code, builder, constants, colorConstant)
        applyRegex(code, builder, "@[A-Za-z0-9_.]+", colorAnnotationOrAttr)
        applyRegex(code, builder, "\\b[0-9]+(\\.[0-9]+)?\\b", colorNumber)
        applyRegex(code, builder, "\"\"\"[\\s\\S]*?\"\"\"|'''[\\s\\S]*?'''|\"(\\\\.|[^\"])*\"|'(\\\\.|[^'])*'", colorString)
        applyRegex(code, builder, "#.*", colorComment)
    }

    private fun highlightPhp(code: String, builder: AnnotatedString.Builder) {
        val keywords = "\\b(function|class|public|private|protected|static|return|if|else|elseif|switch|case|break|continue|for|foreach|as|while|do|try|catch|finally|throw|new|extends|implements|namespace|use|echo|print)\\b"
        applyRegex(code, builder, keywords, colorKeyword, fontWeight = FontWeight.Bold)
        applyRegex(code, builder, "\\$[a-zA-Z0-9_]+", colorTypeOrTag) // PHP Variables
        applyRegex(code, builder, "\\b[0-9]+(\\.[0-9]+)?\\b", colorNumber)
        applyRegex(code, builder, "\"(\\\\.|[^\"])*\"|'(\\\\.|[^'])*'", colorString)
        applyRegex(code, builder, "//.*|/\\*[\\s\\S]*?\\*/|#.*", colorComment)
    }

    private fun highlightSql(code: String, builder: AnnotatedString.Builder) {
        val keywords = "\\b(SELECT|FROM|WHERE|INSERT|INTO|UPDATE|DELETE|JOIN|LEFT|RIGHT|INNER|OUTER|ON|GROUP|BY|ORDER|HAVING|LIMIT|OFFSET|CREATE|TABLE|ALTER|DROP|INDEX|PRIMARY|KEY|FOREIGN|REFERENCES|NOT|NULL|DEFAULT|AND|OR|AS|IN|BETWEEN|LIKE|UNION|ALL)\\b"
        applyRegex(code, builder, keywords, colorKeyword, Pattern.CASE_INSENSITIVE, fontWeight = FontWeight.Bold)
        applyRegex(code, builder, "\\b[0-9]+(\\.[0-9]+)?\\b", colorNumber)
        applyRegex(code, builder, "'(\\\\.|[^'])*'", colorString)
        applyRegex(code, builder, "--.*|/\\*[\\s\\S]*?\\*/", colorComment)
    }

    private fun highlightShell(code: String, builder: AnnotatedString.Builder) {
        val keywords = "\\b(if|then|else|elif|fi|for|in|do|done|while|until|case|esac|function|select|return|exit)\\b"
        applyRegex(code, builder, keywords, colorKeyword, fontWeight = FontWeight.Bold)
        applyRegex(code, builder, "\\$\\{?[a-zA-Z0-9_]+\\}?", colorTypeOrTag) // Variables
        applyRegex(code, builder, "\"(\\\\.|[^\"])*\"|'(\\\\.|[^'])*'", colorString)
        applyRegex(code, builder, "#.*", colorComment)
    }

    private fun highlightMarkdown(code: String, builder: AnnotatedString.Builder) {
        // Headers
        applyRegex(code, builder, "^#{1,6}\\s+.*", colorTypeOrTag, Pattern.MULTILINE, fontWeight = FontWeight.Bold)
        // Code blocks
        applyRegex(code, builder, "```[\\s\\S]*?```|`[^`]+`", colorString)
        // Links
        applyRegex(code, builder, "\\[[^\\]]+\\]\\([^\\)]+\\)", colorAnnotationOrAttr)
        // Bold / Italics
        applyRegex(code, builder, "\\*\\*[^*]+\\*\\*|__[^_]+__", colorKeyword, fontWeight = FontWeight.Bold)
    }

    private fun highlightGeneric(code: String, builder: AnnotatedString.Builder) {
        // Common strings, numbers and comments
        applyRegex(code, builder, "\"(\\\\.|[^\"])*\"|'(\\\\.|[^'])*'", colorString)
        applyRegex(code, builder, "\\b[0-9]+(\\.[0-9]+)?\\b", colorNumber)
        applyRegex(code, builder, "//.*|/\\*[\\s\\S]*?\\*/|#.*", colorComment)
    }

    private fun applyRegex(
        text: String,
        builder: AnnotatedString.Builder,
        regex: String,
        color: Color,
        flags: Int = 0,
        fontWeight: FontWeight? = null
    ) {
        try {
            val pattern = Pattern.compile(regex, flags)
            val matcher = pattern.matcher(text)
            while (matcher.find()) {
                val start = matcher.start()
                val end = matcher.end()
                if (start in 0..text.length && end in 0..text.length && start < end) {
                    builder.addStyle(
                        SpanStyle(color = color, fontWeight = fontWeight),
                        start,
                        end
                    )
                }
            }
        } catch (e: Exception) {
            // Ignore regex error
        }
    }
}
