# Itinerary — Category Spec

How activity categories look on the Itinerary screen, based on the IRIS design system. Every activity in the timeline has a colored icon tile to the left of its title.

## Categories

| Label (ID) | Label (EN) | DB key (`itinerary_items.category`) | Icon (Material Symbols Rounded) | Tile color | Icon color | Icon contrast |
| --- | --- | --- | --- | --- | --- | --- |
| Wisata | Explore | `ATTRACTION` | `attractions` (default), or a place-specific icon | `accent-blue` #00A5EC | `on-accent` white | 2.8:1 ⚠ |
| Kuliner | Eat | `FOOD` | `restaurant` | `accent-orange` #FF5722 | `on-accent` white | 3.2:1 ✓ |
| Perjalanan | Travel | `TRANSPORT` | `directions_car` (land), `flight` (air), `directions_boat` (sea) | `accent-teal` #00A896 | `on-accent` white | 2.98:1 ⚠ |
| Menginap | Stay | `LODGING` | `hotel` | `accent-purple` #7C2D92 | `on-accent` white | 7.9:1 ✓ |
| Belanja | Shop | `SHOPPING` | `shopping_bag` | `accent-yellow` #FFB800 | `on-accent` white | 1.7:1 ⚠ |
| Lainnya | Other | `OTHER` | `event` | `accent-brown` #844C1C | `on-accent` white | 6.9:1 ✓ |

Wisata may use a more specific icon for the type of place, as long as the tile stays blue: `beach_access` (beach), `temple_hindu` (temple), `theater_comedy` (show), `landscape` or `forest` (nature), `museum` (museum), `hiking` (trekking).

### Copywriting

Itinerary category labels read as **activities** in a schedule, not as types of things. English uses short verbs so they line up (Explore, Eat, Travel, Stay, Shop).

- **Wisata / Explore**: beaches, temples, museums, nature, shows. "Atraksi" is not used because in Indonesian it usually means a performance.
- **Kuliner / Eat**: eating at a restaurant, warung, or café, or street snacks.
- **Perjalanan / Travel**: flights, boats, car rental, ojek, moving between cities.
- **Menginap / Stay**: check-in, check-out, and staying the night.

**Expenses use the same enum with noun labels**, because nouns fit better when talking about money: Aktivitas / Activities (`ATTRACTION`), Makan / Food (`FOOD`), Transport (`TRANSPORT`), Penginapan / Lodging (`LODGING`), Belanja / Shopping (`SHOPPING`), Lainnya / Other (`OTHER`). Each value keeps the same tile color in both places, so Transport is teal and Aktivitas is blue in the expense donut and list too.

Lainnya uses brown (`accent-brown`), a calm and neutral color for a general category.

**Current decision: every icon on a tile is white (`on-accent`).** Contrast note (the limit for icons is 3:1): white on blue (2.8:1), teal (2.98:1), and yellow (1.7:1) is below the limit. If it needs raising later, the option is to darken those tile colors.

## Places from AI recommendations

A place saved with a right swipe has no day yet, so it sits in the **Unscheduled** box above the day list (and in its full list, opened from "See all"):

| Element | Spec |
| --- | --- |
| Tile | The place's category color and icon (e.g. Wisata blue), the same as a scheduled activity |
| Supporting text | Small `auto_awesome` icon in `tertiary` #7C2D92 + "Wisata · ± 1 h 30 min" (category and AI duration estimate), `body-small`, `on-surface-variant`; manual items show an `edit` icon + "Added manually" |
| Trailing | An outlined "Jadwalkan" (Schedule) button; the full list adds a ⋮ menu (remove) |
| Box | `surface-container-low` #F9F9F9, 1dp dashed border #C1C1C1, count badge in `secondary` #D92D7B, "Lihat semua" link |
| After scheduling | The item leaves the box and appears on the chosen day; its AI origin stays in `source = AI` |

Scheduling opens a bottom sheet: pick a day (2×2 cards with the number of activities and the end time of each day), the time is filled in at the end of that day (from the end of the last activity, or 09:00 on an empty day) with the AI duration, "Change" opens the TimePicker, then "Jadwalkan ke Hari n".

## Timeline row anatomy

| Element | Spec |
| --- | --- |
| Time column | 44dp wide, time in `title-small` (14/20 bold), tabular figures, format `09.00` |
| Time line | 2dp `outline-variant` #E2E2E2, connecting activities; none after the last activity |
| Tile | 40 × 40dp, radius 12dp (`shape-md`), tile color from the table |
| Icon | 22dp, fill 1 (filled), weight 500 |
| Activity title | 16/24 semibold, `on-surface` |
| Supporting text | `body-medium` (14/20), `on-surface-variant`, the category or place name |
| Drag handle | `drag_indicator` 24dp `outline` #777777 at the end of the row |
| Spacing | Time column to tile 12dp; tile to title 12dp; between activities 16dp |

## Dark theme

Every tile uses a bold color (`accent-*`) with a white icon, and those values are the same in both themes. No special dark values are needed.

## Database note

The `category` column is part of `itinerary_items` in `db/migration/V1__init.sql`, with `DEFAULT 'OTHER'` and a CHECK on `ATTRACTION`, `FOOD`, `TRANSPORT`, `LODGING`, `SHOPPING`, `OTHER`. `expenses.category` and `recommendation_feedback.category` use the same CHECK list. No separate migration is needed, because no database has been created from V1 yet.

## Compose

```kotlin
// shared/commonMain — same values as the CHECK in the database
enum class Category { ATTRACTION, FOOD, TRANSPORT, LODGING, SHOPPING, OTHER }

// composeApp/commonMain — labels through Compose Resources
val Category.itineraryLabel: StringResource
    get() = when (this) {
        Category.ATTRACTION -> Res.string.itinerary_category_attraction
        Category.FOOD -> Res.string.itinerary_category_food
        Category.TRANSPORT -> Res.string.itinerary_category_transport
        Category.LODGING -> Res.string.itinerary_category_lodging
        Category.SHOPPING -> Res.string.itinerary_category_shopping
        Category.OTHER -> Res.string.itinerary_category_other
    }

val Category.expenseLabel: StringResource
    get() = when (this) {
        Category.ATTRACTION -> Res.string.expense_category_attraction
        Category.FOOD -> Res.string.expense_category_food
        Category.TRANSPORT -> Res.string.expense_category_transport
        Category.LODGING -> Res.string.expense_category_lodging
        Category.SHOPPING -> Res.string.expense_category_shopping
        Category.OTHER -> Res.string.expense_category_other
    }

val Category.icon: String
    get() = when (this) {
        Category.ATTRACTION -> "attractions"
        Category.FOOD -> "restaurant"
        Category.TRANSPORT -> "directions_car"
        Category.LODGING -> "hotel"
        Category.SHOPPING -> "shopping_bag"
        Category.OTHER -> "event"
    }

@Composable
fun Category.tileColors(): Pair<Color, Color> {
    val accents = LocalIrisAccents.current
    return when (this) {
        Category.ATTRACTION -> accents.blue.color to OnAccent
        Category.FOOD -> accents.orange.color to OnAccent
        Category.TRANSPORT -> accents.teal.color to OnAccent
        Category.LODGING -> accents.purple.color to OnAccent
        Category.SHOPPING -> accents.yellow.color to OnAccent
        Category.OTHER -> accents.brown.color to OnAccent
    }
}
// Usage: Text(stringResource(category.itineraryLabel))
```

`composeResources/values/strings.xml` (default, English):

```xml
<resources>
    <string name="itinerary_category_attraction">Explore</string>
    <string name="itinerary_category_food">Eat</string>
    <string name="itinerary_category_transport">Travel</string>
    <string name="itinerary_category_lodging">Stay</string>
    <string name="itinerary_category_shopping">Shop</string>
    <string name="itinerary_category_other">Other</string>

    <string name="expense_category_attraction">Activities</string>
    <string name="expense_category_food">Food</string>
    <string name="expense_category_transport">Transport</string>
    <string name="expense_category_lodging">Lodging</string>
    <string name="expense_category_shopping">Shopping</string>
    <string name="expense_category_other">Other</string>
</resources>
```

`composeResources/values-in/strings.xml` (Indonesian):

```xml
<resources>
    <string name="itinerary_category_attraction">Wisata</string>
    <string name="itinerary_category_food">Kuliner</string>
    <string name="itinerary_category_transport">Perjalanan</string>
    <string name="itinerary_category_lodging">Menginap</string>
    <string name="itinerary_category_shopping">Belanja</string>
    <string name="itinerary_category_other">Lainnya</string>

    <string name="expense_category_attraction">Aktivitas</string>
    <string name="expense_category_food">Makan</string>
    <string name="expense_category_transport">Transport</string>
    <string name="expense_category_lodging">Penginapan</string>
    <string name="expense_category_shopping">Belanja</string>
    <string name="expense_category_other">Lainnya</string>
</resources>
```

The enum lives in the `shared` module so it matches the `CHECK` values in the database. Labels and icons exist only on the UI side, so the server does not depend on language.

## Drag and drop (reorder)

Activities in the timeline can be moved with a long press or with the `drag_indicator` handle at the end of the row. Times follow the new order; to set a specific time, the user uses the Edit activity form.

### Time rule: durations kept, schedule packed

1. Only the affected range of positions is recalculated, from the original position to the target position.
2. That range is laid out back to back with no gaps, starting from the start time of the top position in the range.
3. Each activity's duration (`end_time − start_time`) does not change.
4. Activities outside the range do not change. Because the range is packed, the schedule never ends later than before, so it can never overlap the next activity.
5. Empty gaps inside the range disappear. Time that matters (lunch, rest, check-in) is best recorded as an activity.
6. Activities without a time are only reordered.

### Simulations (also used as test cases)

No gaps — item 1 moved to position 3:

| Position | Before | After |
| --- | --- | --- |
| 1 | Item 1 · 10:00–11:25 | Item 2 · 10:00–10:35 |
| 2 | Item 2 · 11:25–12:00 | Item 3 · 10:35–12:35 |
| 3 | Item 3 · 12:00–14:00 | Item 1 · 12:35–14:00 |
| 4 | Item 4 · 14:00–17:00 | Item 4 · 14:00–17:00 (unchanged) |

With a gap — item 2 moved to position 1:

| Position | Before | After |
| --- | --- | --- |
| 1 | Item 1 · 10:00–11:00 | Item 2 · 10:00–10:30 |
| 2 | Item 2 · 13:30–14:00 | Item 1 · 10:30–11:30 |

Canvas example — Uluwatu (16:00–17:30) moved to position 1 on Day 1: Uluwatu 09:00–10:30, Pantai Melasti 10:30–12:30, lunch unchanged 12:30–13:30, Tari Kecak unchanged 18:00–19:00.

### Screen flow

1. **Dragging** — the dragged card only gets a 2dp `primary` border (no tilt or extra shadow) and the text "Mulai 09.00 · 1,5 jam" in `primary`, shown right at its target position; new times in the time column use `primary`. The FAB is hidden while dragging. Hint above the list: "Lepas untuk mengubah urutan. Seret ke chip hari untuk pindah hari." (Release to reorder. Drag onto a day chip to move to another day.)
2. **Confirmation dialog** (after release, only if a time changes) — title "Ubah urutan dan jam?", the activities whose times change (old time struck through → new time in `primary`), a note about activities that do not change, a "Jangan tanya lagi" (Don't ask again) checkbox, and "Batal" (Cancel) and "Ubah jadwal" (Change schedule) buttons. Cancel restores the original order.
3. **After the change** — a snackbar "Jadwal Hari 1 dirapatkan" with an "Urungkan" (Undo) action (`inverse-primary`), shown even when the dialog is turned off with "Don't ask again".

Moving to another day: drag a scheduled activity onto a day chip at the top; the target chip lights up in `primary-container` with a `primary` border. The activity goes to the end of the target day, starting when the last activity of that day ends (or at its original time if that day is empty), with its duration kept. Places in the Unscheduled box are not dragged; they use the Schedule button and sheet.

The "Don't ask again" preference is stored locally per device and can be turned back on from the Account page.

### Code (`shared` module)

```kotlin
data class Slot(val id: String, val start: LocalTime, val end: LocalTime)

fun reorder(items: List<Slot>, from: Int, to: Int): List<Slot> {
    if (from == to) return items
    val lo = minOf(from, to)
    val hi = maxOf(from, to)
    val moved = items.toMutableList().apply { add(to, removeAt(from)) }
    var cursor = items[lo].start.min()
    for (i in lo..hi) {
        val duration = moved[i].end.min() - moved[i].start.min()
        moved[i] = moved[i].copy(start = cursor.toTime(), end = (cursor + duration).toTime())
        cursor += duration
    }
    return moved
}

private fun LocalTime.min() = hour * 60 + minute
private fun Int.toTime() = LocalTime(this / 60, this % 60)
```

The same function fills the dialog (compare `items` with the `reorder` result and list the ones whose times changed).

### API

One drag changes several items, so they are sent in one request and saved in one transaction:

```
PUT /trips/{tripId}/days/{date}/order
{
  "items": [
    { "id": "…", "sortOrder": 1.0, "startTime": "09:00", "endTime": "10:30" },
    { "id": "…", "sortOrder": 2.0, "startTime": "10:30", "endTime": "12:30" }
  ]
}
```

The columns already exist in `itinerary_items`: `day_date`, `start_time`, `end_time`, `sort_order`. Drag-and-drop library for Compose Multiplatform lists: Reorderable (`sh.calvin.reorderable`); check the latest version at setup.
