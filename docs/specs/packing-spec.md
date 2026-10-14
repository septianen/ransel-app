# Packing — Category Spec

How categories look on the Packing screen, based on the IRIS design system. Each category shows a colored icon tile in its group header.

## Categories

| Category (ID / EN) | DB key (`packing_items.category`) | Icon (Material Symbols Rounded) | Tile color | Icon color | Icon contrast |
| --- | --- | --- | --- | --- | --- |
| Pakaian / Clothing | `CLOTHING` | `apparel` | `accent-purple` #7C2D92 | White #FFFFFF | 7.9:1 ✓ |
| Dokumen / Documents | `DOCUMENTS` | `badge` | `accent-blue` #00A5EC | White #FFFFFF | 2.8:1 ⚠ |
| Elektronik / Electronics | `ELECTRONICS` | `power` | `accent-orange` #FF5722 | White #FFFFFF | 3.2:1 ✓ |
| Perlengkapan mandi / Toiletries | `TOILETRIES` | `soap` | `accent-teal` #00A896 | White #FFFFFF | 2.98:1 ⚠ |
| Kesehatan / Health | `HEALTH` | `medical_services` | `accent-red` #D82D3B | White #FFFFFF | 4.8:1 ✓ |
| Lainnya / Other | `OTHER` | `category` | `accent-brown` #844C1C | White #FFFFFF | 6.9:1 ✓ |

Clothing, Documents, and Electronics are already used in the design. The other three follow the same pattern and are ready as database enum values.

**Current decision: every icon on an accent tile is white**, for visual consistency.

Contrast note (the limit for icons is 3:1): white on blue (2.8:1) and teal (2.98:1) is slightly below the limit. Icons use the single `on-accent` token (white) in IRIS. If contrast needs raising later, the remaining option is to darken those tile colors, because IRIS no longer has per-accent `on-accent-*` colors.

## Category header anatomy

| Element | Spec |
| --- | --- |
| Tile | 32 × 32dp, radius 10dp, tile color from the table |
| Icon | 20dp, fill 1 (filled), weight 500 |
| Category name | `title-medium` (16/24, bold), `on-surface` |
| Count | `label-medium` (12/16, bold), `on-surface-variant`, tabular figures, format `3/5` |
| Complete state | Replace the count with a `primary-container` tag + `check_circle` icon + the text "Lengkap" (Complete) |
| Spacing | Tile to name 12dp; header to first item 4dp; between groups 24dp |

## Item row

| Element | Unchecked | Checked |
| --- | --- | --- |
| Checkbox | 22dp, radius 6dp, 2dp `outline` #777777 border | `primary` #008808 fill, white check weight 600 |
| Item name | 16/24 semibold, `on-surface` | 16/24 regular, `on-surface-variant`, struck through |
| Quantity | 14/20 bold, `on-surface`, format `×3` | 14/20 bold, `on-surface-variant` |
| Row height | At least 52dp, the whole row is the touch target | Same |
| Divider | 1dp `outline-variant` #E2E2E2 between rows in one group | Same |

## Packing menu and templates (Phase 4)

The ⋮ menu in the Packing header holds: Pakai template (Use template), Simpan sebagai template (Save as template), and Hapus semua centang (Uncheck all). There is no template button on the screen itself.

- **Use template:** a bottom sheet with built-in templates (beach, city trip, mountain hike) and the user's own templates, a preview of the contents per category, and an option to skip items already on the list.
- **Save as template:** a bottom sheet with the template name, a summary of the contents per category, a "Keep quantities" switch, and a "Share with trip members" switch. The checked state is not saved; every item starts unchecked.

## Dark theme

Every tile uses a bold color (`accent-*`) with a white icon, and those values are the same in both themes. No special dark values are needed.

## Compose

```kotlin
enum class PackingCategory(val icon: String) {
    CLOTHING("apparel"),
    DOCUMENTS("badge"),
    ELECTRONICS("power"),
    TOILETRIES("soap"),
    HEALTH("medical_services"),
    OTHER("category"),
}

// Labels come from Compose Resources: packing_category_clothing = "Clothing" (values/)
// and "Pakaian" (values-in/), and so on for each value.

@Composable
fun PackingCategory.tileColors(): Pair<Color, Color> {
    val accents = LocalIrisAccents.current
    return when (this) {
        PackingCategory.CLOTHING -> accents.purple.color to OnAccent
        PackingCategory.DOCUMENTS -> accents.blue.color to OnAccent
        PackingCategory.ELECTRONICS -> accents.orange.color to OnAccent
        PackingCategory.TOILETRIES -> accents.teal.color to OnAccent
        PackingCategory.HEALTH -> accents.red.color to OnAccent
        PackingCategory.OTHER -> accents.brown.color to OnAccent
    }
}
```

The enum lives in the `shared` module so it matches the `CHECK` values in the `packing_items.category` column.
