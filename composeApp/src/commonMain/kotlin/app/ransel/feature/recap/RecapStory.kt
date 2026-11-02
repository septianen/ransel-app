package app.ransel.feature.recap

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp

const val RECAP_STORY_WIDTH = 1080
const val RECAP_STORY_HEIGHT = 1920

/** The text of the shared picture. [perPerson] null = a trip without expenses: the cell is left out. */
data class RecapStoryText(
    val finished: String,
    val title: String,
    val dates: String,
    val places: String,
    val placesLabel: String,
    val days: String,
    val daysLabel: String,
    val perPerson: String?,
    val perPersonLabel: String,
    val people: String,
    val peopleLabel: String,
    val favourites: String,
    val tagline: String,
)

private val Green = Color(0xFF008808)
private val Ink = Color(0xFF1B1B1B)
private val Muted = Color(0xFF525252)
private val Dash = Color(0xFFBBBBBB)

private const val COLUMN_LEFT = 150f
private const val COLUMN_RIGHT = 570f
private const val TICKET_WIDTH = 780
private const val FREE_WIDTH = 8000

/** Height the two-line title adds, and what leaving out the places line takes off. The ticket's bottom stays put. */
private const val TWO_LINE_TITLE_EXTRA = 56f
private const val NO_PLACES_CUT = 125f

/**
 * Canvas "Rekap", board RekapStory: the picture people share, drawn straight onto a 1080×1920 bitmap in pixels, so it
 * does not depend on the phone's density, font scale, theme or screen width. Always the light brand colours. What
 * matters stays within y 230–1621, clear of Instagram's buttons. [mascot] is `splash_peeking`.
 */
fun renderRecapStory(text: RecapStoryText, mascot: ImageBitmap, measurer: TextMeasurer, family: FontFamily?): ImageBitmap {
    val bitmap = ImageBitmap(RECAP_STORY_WIDTH, RECAP_STORY_HEIGHT)
    val pixels = Density(1f, 1f)
    fun measure(value: String, size: Int, line: Int, weight: FontWeight, color: Color, width: Int = TICKET_WIDTH, maxLines: Int = 1, align: TextAlign = TextAlign.Start) =
        measurer.measure(
            value,
            TextStyle(fontFamily = family, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, color = color, textAlign = align),
            overflow = TextOverflow.Ellipsis,
            softWrap = maxLines > 1,
            maxLines = maxLines,
            constraints = Constraints.fixedWidth(width),
            density = pixels,
            layoutDirection = LayoutDirection.Ltr,
        )

    // Measured wider than the picture: an ellipsized line would not report that it overflowed.
    val twoLines = measure(text.title, 84, 84, FontWeight.ExtraBold, Ink, width = FREE_WIDTH).multiParagraph.getLineWidth(0) > TICKET_WIDTH
    val title = if (twoLines) measure(text.title, 66, 70, FontWeight.ExtraBold, Ink, maxLines = 2) else measure(text.title, 84, 84, FontWeight.ExtraBold, Ink)
    val titleExtra = if (twoLines) TWO_LINE_TITLE_EXTRA else 0f
    val hasPlaces = text.favourites.isNotBlank()
    val cut = if (hasPlaces) 0f else NO_PLACES_CUT
    val upper = cut - titleExtra // mascot, top of the ticket, label, title
    val lower = cut // everything under the title

    CanvasDrawScope().draw(pixels, LayoutDirection.Ltr, Canvas(bitmap), Size(RECAP_STORY_WIDTH.toFloat(), RECAP_STORY_HEIGHT.toFloat())) {
        drawRect(Green)
        // the mascot stands behind the ticket, which covers its lower part
        drawImage(
            mascot,
            dstOffset = IntOffset(382, (255 + upper).toInt()),
            dstSize = IntSize(315, 411),
            filterQuality = FilterQuality.High,
        )
        val ticketTop = 570f + upper
        drawRoundRect(Color.White, Offset(90f, ticketTop), Size(900f, 1450f - ticketTop), CornerRadius(48f))

        place(measure(text.finished, 36, 36, FontWeight.Bold, Muted), COLUMN_LEFT, 632 + upper)
        place(title, COLUMN_LEFT, 689 + upper)
        place(measure(text.dates, 38, 38, FontWeight.Normal, Muted), COLUMN_LEFT, 795 + lower)

        // the tear line, with a notch cut out of each side
        drawLine(
            Dash, Offset(140f, 900 + lower), Offset(940f, 900 + lower), strokeWidth = 4f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f)),
        )
        drawCircle(Green, 32f, Offset(90f, 900 + lower))
        drawCircle(Green, 32f, Offset(990f, 900 + lower))

        fun stat(value: String, label: String, x: Float, valueTop: Float, labelTop: Float) {
            place(measure(value, 88, 88, FontWeight.ExtraBold, Green, width = 400), x, valueTop + lower)
            place(measure(label, 34, 34, FontWeight.Normal, Muted, width = 400), x, labelTop + lower)
        }
        stat(text.places, text.placesLabel, COLUMN_LEFT, 950f, 1049f)
        stat(text.days, text.daysLabel, COLUMN_RIGHT, 950f, 1049f)
        if (text.perPerson != null) {
            // a long amount gets smaller rather than running into the next column
            var size = 72
            var amount = measure(text.perPerson, size, size, FontWeight.ExtraBold, Green, width = FREE_WIDTH)
            while (amount.multiParagraph.getLineWidth(0) > 400f && size > 36) {
                size -= 6
                amount = measure(text.perPerson, size, size, FontWeight.ExtraBold, Green, width = FREE_WIDTH)
            }
            place(amount, COLUMN_LEFT, 1145f + (72 - size) / 2 + lower)
            place(measure(text.perPersonLabel, 34, 34, FontWeight.Normal, Muted, width = 400), COLUMN_LEFT, 1227 + lower)
            stat(text.people, text.peopleLabel, COLUMN_RIGHT, 1130f, 1227f)
        } else {
            stat(text.people, text.peopleLabel, COLUMN_LEFT, 1130f, 1227f)
        }
        if (hasPlaces) place(measure(text.favourites, 30, 44, FontWeight.Bold, Ink, maxLines = 2), COLUMN_LEFT, 1310f)

        // the brand under the ticket: 56 wide mark, 20 gap, wordmark, centred together
        val word = measure("RANSEL", 72, 76, FontWeight.ExtraBold, Color.White, width = 600)
        val wordWidth = word.multiParagraph.getLineWidth(0)
        val markLeft = (RECAP_STORY_WIDTH - (56f + 20f + wordWidth)) / 2
        translate(markLeft, 1500f) { drawRanselMark(Color.White, Green, 56f) }
        place(word, markLeft + 76f, 1500f)
        place(measure(text.tagline, 28, 36, FontWeight.Bold, Color.White, width = RECAP_STORY_WIDTH, align = TextAlign.Center), 0f, 1585f)
    }
    return bitmap
}

private fun DrawScope.place(text: TextLayoutResult, x: Float, y: Float) = drawText(text, topLeft = Offset(x, y))

private val MarkHandle = PathParser().parsePathString("M45 33 a9 9 0 0 1 18 0").toPath()
private val MarkBody = PathParser()
    .parsePathString("M31 52 a21 21 0 0 1 21-21 h4 a21 21 0 0 1 21 21 v8 c0 2.5 -1 4 -2.5 5.5 c1.5 1.5 2.5 3 2.5 5.5 v1 a12 12 0 0 1 -12 12 H57 L53 78 V84 H43 a12 12 0 0 1 -12 -12 z")
    .toPath()

/** The RANSEL mark (a viewBox of 46 × 62.5) at [width], with its top-left at the origin. [gap] colours the pocket. */
fun DrawScope.drawRanselMark(color: Color, gap: Color, width: Float) {
    val k = width / 46f
    withTransform({
        scale(k, k, Offset.Zero)
        translate(-31f, -21.5f)
    }) {
        drawPath(MarkHandle, color, style = Stroke(width = 5f, cap = StrokeCap.Round))
        drawPath(MarkBody, color)
        drawRoundRect(gap, Offset(44f, 49f), Size(20f, 5f), CornerRadius(2.5f))
    }
}
