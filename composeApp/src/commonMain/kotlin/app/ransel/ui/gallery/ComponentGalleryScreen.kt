package app.ransel.ui.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ransel.shared.model.Category
import app.ransel.shared.model.PackingCategory
import app.ransel.ui.components.BudgetProgress
import app.ransel.ui.components.CategoryIcon
import app.ransel.ui.components.ConfirmDialog
import app.ransel.ui.components.FullScreenState
import app.ransel.ui.components.IconFab
import app.ransel.ui.components.LoadingButton
import app.ransel.ui.components.LoadingOutlinedButton
import app.ransel.ui.components.MoneyText
import app.ransel.ui.components.PackingCategoryIcon
import app.ransel.ui.components.IrisTextField
import app.ransel.ui.components.SectionHeader
import app.ransel.ui.components.TripCover
import app.ransel.ui.components.TripHeader
import app.ransel.ui.components.TripTab
import app.ransel.ui.components.CaveCartLoading
import app.ransel.ui.components.UndoSnackbar
import org.jetbrains.compose.resources.stringResource
import ransel.composeapp.generated.resources.Res
import ransel.composeapp.generated.resources.explore_loading_1
import ransel.composeapp.generated.resources.explore_loading_2
import ransel.composeapp.generated.resources.explore_loading_3
import ransel.composeapp.generated.resources.explore_loading_4
import ransel.composeapp.generated.resources.explore_loading_hint
import app.ransel.ui.components.AdaptiveFormScreen
import app.ransel.ui.components.AdaptiveSheet
import app.ransel.ui.components.VoteOptionCard
import app.ransel.ui.components.FormScaffold
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbols
import androidx.compose.material3.ExperimentalMaterial3Api

/**
 * Debug-only screen that lists every shared component, light and dark together
 * (via [IrisPreview]), for checking against the design. Keep it in step with `ui/components/`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComponentGalleryScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var showDialog by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }
    var showForm by remember { mutableStateOf(false) }
    var showAdaptiveForm by remember { mutableStateOf(false) }

    if (showForm) {
        FormScaffold(
            title = "Buat trip",
            onBack = { showForm = false },
            saveLabel = "Simpan",
            onSave = { showForm = false },
        ) {
            repeat(12) { Text("Field ${it + 1}", style = MaterialTheme.typography.bodyLarge) }
        }
        return
    }

    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.padding(horizontal = 8.dp)) { Text("← Back") }
        IrisPreview {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Section("FullScreenState") {
                    FullScreenState(
                        modifier = Modifier.height(560.dp),
                        title = "Waduh, sinyalnya lagi tidur",
                        body = "Periksa koneksimu, lalu coba lagi.",
                        errorCode = "NETWORK_TIMEOUT",
                        primaryLabel = "Coba lagi",
                        onPrimary = {},
                    )
                }
                Section("TripHeader") {
                    TripHeader("Bali 4 hari", "20–23 Nov 2026", TripTab.ITINERARY, {}, {}, onEdit = {})
                }
                Section("CategoryIcon / PackingCategoryIcon") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Category.entries.forEach { CategoryIcon(it) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PackingCategory.entries.forEach { PackingCategoryIcon(it) }
                    }
                }
                Section("TripCover") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("bali", "tokyo", "lombok", "jogja", "bandung").forEach {
                            TripCover(seed = it, modifier = Modifier.size(56.dp))
                        }
                    }
                }
                Section("IrisTextField") {
                    IrisTextField("Nama trip", "Bali 4 hari", {})
                    IrisTextField("Budget", "", {}, labelSuffix = "(opsional)", prefix = "Rp", placeholder = "0")
                    IrisTextField("Nama trip", "", {}, error = "Wajib diisi")
                }
                Section("MoneyText") {
                    MoneyText(50_000, "IDR", style = MaterialTheme.typography.titleMedium)
                    MoneyText(1_250, "USD", style = MaterialTheme.typography.titleMedium)
                }
                Section("HeroCard") {
                    app.ransel.ui.components.HeroCard(label = "Bali 4 hari", value = "12 hari lagi", symbol = Symbols.calendarMonth) {
                        Text("20–23 Nov 2026", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Section("BudgetProgress") {
                    BudgetProgress(3_250_000, 5_000_000, "IDR")
                }
                Section("IconFab") {
                    IconFab(Symbols.add, label = "Tambah", onClick = {})
                }
                Section("SegmentedTabs") {
                    app.ransel.ui.components.SegmentedTabs(listOf("Pribadi", "Bersama"), selected = "Pribadi", label = { it }, onSelect = {})
                }
                Section("StatusTag") {
                    app.ransel.ui.components.StatusTag("Diputuskan")
                }
                Section("SectionHeader") {
                    SectionHeader("Belum dijadwalkan", actionLabel = "Lihat semua", onAction = {})
                }
                Section("UndoSnackbar") {
                    UndoSnackbar("Jadwal Hari 1 dirapatkan", actionLabel = "Urungkan", onAction = {})
                }
                Section("LoadingButton") {
                    // Tap either button: it shows the spinner for 2 s and ignores clicks meanwhile.
                    var loading by remember { mutableStateOf(false) }
                    LaunchedEffect(loading) { if (loading) { delay(2_000); loading = false } }
                    LoadingButton(onClick = { loading = true }, loading = loading, modifier = Modifier.fillMaxWidth()) { Text("Simpan") }
                    LoadingOutlinedButton(onClick = { loading = true }, loading = loading, modifier = Modifier.fillMaxWidth()) { Text("Tandai lunas") }
                    LoadingButton(onClick = {}, loading = false, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("Disabled") }
                }
                Section("CaveCartLoading") {
                    CaveCartLoading(
                        messages = listOf(
                            stringResource(Res.string.explore_loading_1),
                            stringResource(Res.string.explore_loading_2),
                            stringResource(Res.string.explore_loading_3),
                            stringResource(Res.string.explore_loading_4),
                        ),
                        hint = stringResource(Res.string.explore_loading_hint),
                    )
                }
                Section("VoteOptionCard") {
                    VoteOptionCard("Kereta", voteCount = 4, memberCount = 5, countLabel = "4 suara", subtitle = "Rp250.000", winner = true)
                    VoteOptionCard("Pantai Melasti", voteCount = 2, memberCount = 5, countLabel = "2 dari 5 suka", subtitle = "Wisata")
                }
                Section("ConfirmDialog / AdaptiveSheet / FormScaffold / AdaptiveFormScreen") {
                    Button(onClick = { showDialog = true }) { Text("ConfirmDialog") }
                    Button(onClick = { showSheet = true }) { Text("AdaptiveSheet") }
                    Button(onClick = { showForm = true }) { Text("FormScaffold") }
                    Button(onClick = { showAdaptiveForm = true }) { Text("AdaptiveFormScreen") }
                }
            }
        }
    }

    if (showDialog) {
        ConfirmDialog(
            title = "Hapus trip ini?",
            body = "Semua aktivitas, barang, dan catatan pengeluaran ikut terhapus.",
            confirmLabel = "Ya, hapus",
            dismissLabel = "Batal",
            onConfirm = { showDialog = false },
            onDismiss = { showDialog = false },
            destructive = true,
            symbol = Symbols.delete,
        )
    }
    if (showAdaptiveForm) {
        AdaptiveFormScreen(
            title = "Catat pengeluaran",
            subtitle = "Bali 4 hari · Hari 2",
            onDismiss = { showAdaptiveForm = false },
            confirmDiscard = true,
            footer = { Button(onClick = { showAdaptiveForm = false }, modifier = Modifier.fillMaxWidth()) { Text("Simpan") } },
        ) {
            repeat(20) { Text("Field ${it + 1}", style = MaterialTheme.typography.bodyLarge) }
        }
    }
    if (showSheet) {
        AdaptiveSheet(onDismiss = { showSheet = false }) {
            Text("Tambah barang", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Contoh isi bottom sheet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Section(name: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}
