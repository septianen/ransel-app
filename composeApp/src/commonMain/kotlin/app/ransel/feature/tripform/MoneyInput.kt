package app.ransel.feature.tripform

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Shows an amount typed as plain digits with "." thousand separators ("5000000" → "5.000.000").
 * The state keeps the raw text; an optional decimal part uses "," ("1250000,5" → "1.250.000,5") and is
 * left ungrouped. Used by the budget and the expense amount fields.
 */
object ThousandsVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val decimalAt = raw.indexOf(',')
        val integerLength = if (decimalAt >= 0) decimalAt else raw.length

        val out = StringBuilder()
        val inserted = ArrayList<Boolean>() // per output char: true = a separator we added
        val originalToTransformed = IntArray(raw.length + 1)
        raw.forEachIndexed { i, c ->
            if (i in 1 until integerLength && (integerLength - i) % 3 == 0) {
                out.append('.')
                inserted.add(true)
            }
            originalToTransformed[i] = out.length
            out.append(c)
            inserted.add(false)
        }
        originalToTransformed[raw.length] = out.length

        val transformedToOriginal = IntArray(out.length + 1)
        var seen = 0
        for (k in 0..out.length) {
            transformedToOriginal[k] = seen
            if (k < out.length && !inserted[k]) seen++
        }

        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                originalToTransformed[offset.coerceIn(0, raw.length)]

            override fun transformedToOriginal(offset: Int): Int =
                transformedToOriginal[offset.coerceIn(0, out.length)]
        }
        return TransformedText(AnnotatedString(out.toString()), mapping)
    }
}
