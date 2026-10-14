package app.ransel.feature.tripform

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Shows a digits-only value with "." thousand separators ("5000000" → "5.000.000"). The state keeps raw digits. */
object ThousandsVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val out = StringBuilder()
        digits.forEachIndexed { i, c ->
            if (i > 0 && (digits.length - i) % 3 == 0) out.append('.')
            out.append(c)
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                // a separator sits before every digit whose distance from the end is a multiple of 3
                var separators = 0
                for (i in 1 until offset.coerceAtMost(digits.length)) if ((digits.length - i) % 3 == 0) separators++
                return offset + separators
            }

            override fun transformedToOriginal(offset: Int): Int {
                var original = 0
                var seen = 0
                for (i in out.indices) {
                    if (seen >= offset) break
                    if (out[i] != '.') original++
                    seen++
                }
                return original
            }
        }
        return TransformedText(AnnotatedString(out.toString()), mapping)
    }
}
