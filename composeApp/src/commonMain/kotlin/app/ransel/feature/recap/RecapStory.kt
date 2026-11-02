package app.ransel.feature.recap

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
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

/** The three pictures to pick from (canvas "RANSEL — Rekap Story"). [resource] is the mascot, under composeResources. */
enum class RecapTemplate(val resource: String) {
    TICKET("drawable/splash_peeking.webp"),
    DONE("drawable/ic_recap_floating.webp"),
    BIG_NUMBERS("drawable/ic_recap_hiking.webp"),
}

/** The text of the shared picture. [perPerson] null = a trip without expenses: the cell is left out. */
data class RecapStoryText(
    val finished: String,
    val done: String,
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
private val Sky = Color(0xFF00A5EC)
private val Sun = Color(0xFFFFB800)
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
 * The picture people share, drawn straight onto a 1080×1920 bitmap in pixels, so it does not depend on the phone's
 * density, font scale, theme or screen width. Coordinates are the boards': "1. Tiket", "2. Beres!", "3. Angka besar".
 * No member avatars, total or settled status: those stay in the app.
 */
fun renderRecapStory(template: RecapTemplate, text: RecapStoryText, mascot: ImageBitmap, measurer: TextMeasurer, family: FontFamily?): ImageBitmap {
    val bitmap = ImageBitmap(RECAP_STORY_WIDTH, RECAP_STORY_HEIGHT)
    val writer = StoryWriter(measurer, family)
    CanvasDrawScope().draw(Pixels, LayoutDirection.Ltr, Canvas(bitmap), Size(RECAP_STORY_WIDTH.toFloat(), RECAP_STORY_HEIGHT.toFloat())) {
        when (template) {
            RecapTemplate.TICKET -> drawTicket(text, mascot, writer)
            RecapTemplate.DONE -> drawDone(text, mascot, writer)
            RecapTemplate.BIG_NUMBERS -> drawBigNumbers(text, mascot, writer)
        }
    }
    return bitmap
}

private val Pixels = Density(1f, 1f)

private class StoryWriter(private val measurer: TextMeasurer, private val family: FontFamily?) {
    fun measure(value: String, size: Int, line: Int, weight: FontWeight, color: Color, width: Int = TICKET_WIDTH, maxLines: Int = 1, align: TextAlign = TextAlign.Start) =
        measurer.measure(
            value,
            TextStyle(fontFamily = family, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, color = color, textAlign = align),
            overflow = TextOverflow.Ellipsis,
            softWrap = maxLines > 1,
            maxLines = maxLines,
            constraints = Constraints.fixedWidth(width),
            density = Pixels,
            layoutDirection = LayoutDirection.Ltr,
        )

    /** Measured wider than the picture, because an ellipsized line does not report that it overflowed. */
    fun width(value: String, size: Int, weight: FontWeight): Float =
        measure(value, size, size, weight, Ink, width = FREE_WIDTH).multiParagraph.getLineWidth(0)

    /** The largest size from [start] down to [floor] that fits [maxWidth]: a long title or amount shrinks, it does not run on. */
    fun fit(value: String, start: Int, floor: Int, maxWidth: Int, weight: FontWeight): Int {
        var size = start
        while (size > floor && width(value, size, weight) > maxWidth) size -= 2
        return size
    }
}

/** Board "1. Tiket". What matters stays within y 230–1621, clear of Instagram's buttons. */
private fun DrawScope.drawTicket(text: RecapStoryText, mascot: ImageBitmap, w: StoryWriter) {
    val twoLines = w.width(text.title, 84, FontWeight.ExtraBold) > TICKET_WIDTH
    val title = if (twoLines) w.measure(text.title, 66, 70, FontWeight.ExtraBold, Ink, maxLines = 2) else w.measure(text.title, 84, 84, FontWeight.ExtraBold, Ink)
    val titleExtra = if (twoLines) TWO_LINE_TITLE_EXTRA else 0f
    val hasPlaces = text.favourites.isNotBlank()
    val cut = if (hasPlaces) 0f else NO_PLACES_CUT
    val upper = cut - titleExtra // mascot, top of the ticket, label, title
    val lower = cut // everything under the title

    drawRect(Green)
    // the mascot stands behind the ticket, which covers its lower part
    drawImage(mascot, dstOffset = IntOffset(382, (255 + upper).toInt()), dstSize = IntSize(315, 411), filterQuality = FilterQuality.High)
    val ticketTop = 570f + upper
    drawRoundRect(Color.White, Offset(90f, ticketTop), Size(900f, 1450f - ticketTop), CornerRadius(48f))

    place(w.measure(text.finished, 36, 36, FontWeight.Bold, Muted), COLUMN_LEFT, 632 + upper)
    place(title, COLUMN_LEFT, 689 + upper)
    place(w.measure(text.dates, 38, 38, FontWeight.Normal, Muted), COLUMN_LEFT, 795 + lower)

    // the tear line, with a notch cut out of each side
    drawLine(Dash, Offset(140f, 900 + lower), Offset(940f, 900 + lower), strokeWidth = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f)))
    drawCircle(Green, 32f, Offset(90f, 900 + lower))
    drawCircle(Green, 32f, Offset(990f, 900 + lower))

    fun stat(value: String, label: String, x: Float, valueTop: Float, labelTop: Float) {
        place(w.measure(value, 88, 88, FontWeight.ExtraBold, Green, width = 400), x, valueTop + lower)
        place(w.measure(label, 34, 34, FontWeight.Normal, Muted, width = 400), x, labelTop + lower)
    }
    stat(text.places, text.placesLabel, COLUMN_LEFT, 950f, 1049f)
    stat(text.days, text.daysLabel, COLUMN_RIGHT, 950f, 1049f)
    if (text.perPerson != null) {
        val size = w.fit(text.perPerson, 72, 36, 400, FontWeight.ExtraBold)
        place(w.measure(text.perPerson, size, size, FontWeight.ExtraBold, Green, width = 400), COLUMN_LEFT, 1145f + (72 - size) / 2 + lower)
        place(w.measure(text.perPersonLabel, 34, 34, FontWeight.Normal, Muted, width = 400), COLUMN_LEFT, 1227 + lower)
        stat(text.people, text.peopleLabel, COLUMN_RIGHT, 1130f, 1227f)
    } else {
        stat(text.people, text.peopleLabel, COLUMN_LEFT, 1130f, 1227f)
    }
    if (hasPlaces) place(w.measure(text.favourites, 30, 44, FontWeight.Bold, Ink, maxLines = 2), COLUMN_LEFT, 1310f)

    drawCentredBrand(text.tagline, w, top = 1500f, word = Color.White, mark = Color.White, pocket = Green, tagline = Color.White)
}

/** Board "2. Beres!": the mascot afloat under a big word, the numbers on a white wave. */
private fun DrawScope.drawDone(text: RecapStoryText, mascot: ImageBitmap, w: StoryWriter) {
    drawRect(Sky)
    drawCircle(Sun, 170f, Offset(900f, 180f))
    val heading = "${text.title},"
    val size = w.fit(heading, 72, 40, 940, FontWeight.ExtraBold)
    place(w.measure(heading, size, size, FontWeight.ExtraBold, Color.White, width = 940), 70f, 305f + (72 - size))
    place(w.measure(text.done, 220, 220, FontWeight.ExtraBold, Color.White, width = 1000), 60f, 370f)
    drawImage(mascot, dstOffset = IntOffset(150, 520), dstSize = IntSize(780, 780), filterQuality = FilterQuality.High)
    // the board's wave, drawn from y 1190–1920 into y 1140–1920
    fun y(v: Float) = 1140f + (v - 1190f) * 780f / 730f
    drawPath(
        Path().apply {
            moveTo(0f, y(1240f))
            cubicTo(180f, y(1190f), 360f, y(1280f), 540f, y(1240f))
            cubicTo(720f, y(1200f), 900f, y(1190f), 1080f, y(1240f))
            lineTo(1080f, 1920f)
            lineTo(0f, 1920f)
            close()
        },
        Color.White,
    )
    place(w.measure(text.dates, 36, 36, FontWeight.Normal, Muted, width = 940), 70f, 1242f)
    fun stat(value: String, label: String, x: Float, maxWidth: Int) {
        val valueSize = w.fit(value, 88, 40, maxWidth, FontWeight.ExtraBold)
        place(w.measure(value, valueSize, valueSize, FontWeight.ExtraBold, Ink, width = maxWidth), x, 1320f + (88 - valueSize) / 2)
        place(w.measure(label, 34, 34, FontWeight.Normal, Muted, width = maxWidth), x, 1419f)
    }
    stat(text.places, text.placesLabel, 70f, 250)
    stat(text.days, text.daysLabel, 330f, 220)
    if (text.perPerson != null) stat(text.perPerson, text.perPersonLabel, 560f, 450)
    drawCentredBrand(text.tagline, w, top = 1510f, word = Green, mark = Green, pocket = Color.White, tagline = Muted)
}

/** Board "3. Angka besar": the numbers fill the picture, the mascot walks over a green hill. */
private fun DrawScope.drawBigNumbers(text: RecapStoryText, mascot: ImageBitmap, w: StoryWriter) {
    drawRect(Sun)
    val titleSize = w.fit(text.title, 64, 36, 940, FontWeight.ExtraBold)
    place(w.measure(text.title, titleSize, titleSize, FontWeight.ExtraBold, Ink, width = 940), 70f, 252f + (64 - titleSize))
    place(w.measure(text.dates, 36, 36, FontWeight.Bold, Ink, width = 940), 70f, 333f)
    // the label follows its number, however many digits that has
    fun bigStat(value: String, label: String, top: Float) {
        place(w.measure(value, 300, 300, FontWeight.ExtraBold, Ink, width = 960), 60f, top)
        place(w.measure(label, 64, 64, FontWeight.ExtraBold, Ink, width = 500), 60f + w.width(value, 300, FontWeight.ExtraBold) + 14f, top + 214f)
    }
    bigStat(text.places, text.placesLabel, 388f)
    bigStat(text.days, text.daysLabel, 648f)
    if (text.perPerson != null) {
        val size = w.fit(text.perPerson, 130, 64, 700, FontWeight.ExtraBold)
        place(w.measure(text.perPerson, size, size, FontWeight.ExtraBold, Ink, width = 960), 60f, 982f + (130 - size))
        place(w.measure(text.perPersonLabel, 64, 64, FontWeight.ExtraBold, Ink, width = 500), 70f, 1112f)
    }
    drawPath(
        Path().apply {
            moveTo(0f, 1500f)
            cubicTo(300f, 1440f, 700f, 1500f, 1080f, 1450f)
            lineTo(1080f, 1920f)
            lineTo(0f, 1920f)
            close()
        },
        Green,
    )
    drawImage(mascot, dstOffset = IntOffset(520, 1010), dstSize = IntSize(560, 560), filterQuality = FilterQuality.High)
    translate(70f, 1572f) { drawRanselMark(Color.White, Green, 56f) }
    place(w.measure("RANSEL", 72, 76, FontWeight.ExtraBold, Color.White, width = 600), 146f, 1572f)
    place(w.measure(text.tagline, 28, 36, FontWeight.Bold, Color.White, width = 940), 70f, 1660f)
}

/** Mark, 20 gap and wordmark centred together, the tagline 85 under them. */
private fun DrawScope.drawCentredBrand(text: String, w: StoryWriter, top: Float, word: Color, mark: Color, pocket: Color, tagline: Color) {
    val wordmark = w.measure("RANSEL", 72, 76, FontWeight.ExtraBold, word, width = 600)
    val left = (RECAP_STORY_WIDTH - (56f + 20f + wordmark.multiParagraph.getLineWidth(0))) / 2
    translate(left, top) { drawRanselMark(mark, pocket, 56f) }
    place(wordmark, left + 76f, top)
    place(w.measure(text, 28, 36, FontWeight.Bold, tagline, width = RECAP_STORY_WIDTH, align = TextAlign.Center), 0f, top + 85f)
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
