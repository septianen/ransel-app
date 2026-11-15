package app.ransel.feature.documents

import app.ransel.ui.components.SkeletonList
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.ransel.content.getString
import app.ransel.content.stringResource
import app.ransel.data.repository.TripRepository
import app.ransel.shared.api.ErrorCode
import app.ransel.shared.dto.ItineraryItemDto
import app.ransel.shared.dto.TripDocumentDto
import app.ransel.shared.model.DocumentCategory
import app.ransel.shared.model.DocumentKind
import app.ransel.shared.model.Role as TripRole
import app.ransel.ui.RefreshOnReturn
import app.ransel.ui.components.AdaptiveSheet
import app.ransel.ui.components.FullScreenState
import app.ransel.ui.components.IconFab
import app.ransel.ui.components.IrisTextField
import app.ransel.ui.components.LoadingButton
import app.ransel.ui.components.MemberAvatar
import app.ransel.ui.components.SurfUploadProgress
import app.ransel.ui.components.UndoSnackbar
import app.ransel.ui.preview.IrisPreview
import app.ransel.ui.symbols.Symbol
import app.ransel.ui.symbols.Symbols
import app.ransel.ui.theme.IrisShape
import app.ransel.ui.theme.IrisTheme
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.launch
import app.ransel.feature.documents.viewer.PhotoViewer
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import app.ransel.feature.documents.viewer.PhotoLoad
import app.ransel.feature.documents.viewer.PhotoLoader
import app.ransel.feature.documents.viewer.ViewerPhoto
import app.ransel.feature.expenses.receipt.PhotoThumbnail
import app.ransel.feature.expenses.receipt.rememberThumbnail
import app.ransel.shared.dto.ExpenseDto
import app.ransel.ui.components.ConfirmDialog
import app.ransel.ui.format.formatMoney
import androidx.compose.foundation.layout.height
import ransel.composeapp.generated.resources.receipt_delete_both_title
import ransel.composeapp.generated.resources.receipt_delete_both_body
import ransel.composeapp.generated.resources.receipt_delete_both_confirm
import ransel.composeapp.generated.resources.receipt_viewer_subtitle
import ransel.composeapp.generated.resources.receipt_viewer_open_expense
import ransel.composeapp.generated.resources.documents_group_receipts
import ransel.composeapp.generated.resources.documents_quota_almost_full
import kotlinx.datetime.daysUntil
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import ransel.composeapp.generated.resources.*
import kotlin.time.Instant

/** Design "DokumenTrip": the trip's tickets, confirmations and vouchers by group. [startUpload] opens the add sheet first. */
@Composable
fun DocumentsScreen(
    tripId: String,
    startUpload: Boolean,
    onBack: () -> Unit,
    /** "Buka pengeluaran" in the viewer of a receipt photo; null hides the button. */
    onOpenExpenses: (() -> Unit)? = null,
    viewModel: DocumentsViewModel = koinViewModel(key = "documents-$tripId") { parametersOf(tripId) },
    trips: TripRepository = koinInject(),
) {
    RefreshOnReturn(viewModel::load)
    val state by viewModel.state.collectAsState()
    val trip by trips.observeTrip(tripId).collectAsState(initial = null)
    var adding by remember { mutableStateOf(startUpload) }
    var sheetProblem by remember { mutableStateOf<String?>(null) }
    var viewerRequest by remember { mutableStateOf<ViewerRequest?>(null) }
    // "Struk · ditambahkan %s": the adder is put in by the view model
    val receiptSubtitle = stringResource(Res.string.receipt_viewer_subtitle, "%s")
    val openExpenseLabel = stringResource(Res.string.receipt_viewer_open_expense)
    var viewerHint by remember { mutableStateOf(false) }
    LaunchedEffect(viewerRequest != null) { if (viewerRequest != null) viewerHint = viewModel.viewerHintDue() }
    val snackbar = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(viewModel) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is DocumentsEvent.OpenUrl -> runCatching { uriHandler.openUri(event.url) }
                DocumentsEvent.Added -> { adding = false; snackbar.showSnackbar(getString(Res.string.document_added)) }
                DocumentsEvent.Deleted -> snackbar.showSnackbar(getString(Res.string.document_deleted))
                is DocumentsEvent.Problem -> if (adding) sheetProblem = event.code else {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(getString(problemText(event.code)))
                }
            }
        }
    }
    val current = trip
    val canAdd = current?.myRole?.atLeast(TripRole.EDITOR) == true
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).statusBarsPadding()) {
        DocumentsContent(
            state = state,
            tripTitle = current?.title.orEmpty(),
            tripStart = current?.startDate,
            keepsFiles = viewModel.keepsFiles,
            myMemberId = viewModel.myMemberId,
            isOwner = current?.myRole == TripRole.OWNER,
            onBack = onBack,
            loader = viewModel.photoLoader,
            onOpen = { doc -> if (viewModel.isPhoto(doc)) viewerRequest = viewModel.viewerFor(doc, receiptSubtitle) else viewModel.open(doc) },
            onDelete = viewModel::delete,
        )
        if (canAdd && !state.fromDevice) {
            IconFab(Symbols.add, label = stringResource(Res.string.documents_add), onClick = { sheetProblem = null; adding = true }, modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)) { UndoSnackbar(message = it.visuals.message) }
    }
    viewerRequest?.let { request ->
        PhotoViewer(
            photos = request.photos,
            startIndex = request.startIndex,
            loader = viewModel.photoLoader,
            onClose = { viewerRequest = null },
            prefetch = viewModel.keepsFiles,
            showHint = viewerHint,
            onHintShown = viewModel::viewerOpened,
            actionLabel = openExpenseLabel,
            // a receipt photo leads to its expense (the Pengeluaran tab of the trip)
            action = { photo -> onOpenExpenses?.takeIf { viewModel.expenseOf(photo.id) != null }?.let { open -> { viewerRequest = null; open() } } },
        )
    }
    if (adding && canAdd) {
        AddDocumentSheet(
            activities = state.activities,
            tripStart = current?.startDate,
            saving = state.saving,
            uploadProgress = state.uploadProgress,
            problem = sheetProblem,
            onSave = { title, category, item, file, link -> sheetProblem = null; viewModel.add(title, category, item, file, link) },
            onDismiss = { adding = false },
        )
    }
}

fun problemText(code: String): StringResource = when (code) {
    ErrorCode.FILE_TOO_LARGE -> Res.string.document_error_FILE_TOO_LARGE
    ErrorCode.STORAGE_FULL -> Res.string.document_error_STORAGE_FULL
    ErrorCode.UNSUPPORTED_FILE -> Res.string.document_error_UNSUPPORTED_FILE
    DocumentsViewModel.LINK -> Res.string.document_error_link
    DocumentsViewModel.TITLE -> Res.string.document_error_title
    DocumentsViewModel.OPEN_FAILED -> Res.string.document_open_failed
    else -> Res.string.document_error_generic
}

@Composable
fun DocumentsContent(
    state: DocumentsUiState,
    tripTitle: String,
    tripStart: LocalDate?,
    keepsFiles: Boolean,
    myMemberId: String?,
    isOwner: Boolean,
    onBack: () -> Unit,
    onOpen: (TripDocumentDto) -> Unit,
    onDelete: (TripDocumentDto) -> Unit,
    loader: PhotoLoader = PhotoLoader { PhotoLoad.Failed },
) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack), contentAlignment = Alignment.Center) {
                Symbol(Symbols.arrowBack, contentDescription = stringResource(Res.string.action_back))
            }
            Column(Modifier.padding(start = 4.dp)) {
                Text(stringResource(Res.string.documents_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(Res.string.documents_count, tripTitle, state.documents.size), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
        when {
            state.loading -> SkeletonList()
            state.documents.isEmpty() -> FullScreenState(
                title = stringResource(Res.string.documents_empty_title),
                body = stringResource(Res.string.documents_empty_body),
            )
            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().clip(IrisShape.md).background(scheme.surfaceContainerLow).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Symbol(if (state.fromDevice) Symbols.cloudOff else Symbols.cloudDone, size = 18.dp, tint = scheme.onSurfaceVariant)
                    Text(
                        stringResource(
                            when {
                                state.fromDevice -> Res.string.documents_offline_list
                                keepsFiles -> Res.string.documents_note
                                else -> Res.string.documents_note_web
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                    )
                }
                if (!state.fromDevice) QuotaBar(state.usedBytes, state.limitBytes)
                state.byCategory.forEach { (category, docs) ->
                    Column {
                        Text(stringResource(category.label), style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
                        docs.forEach { doc ->
                            DocumentRow(
                                doc = doc,
                                activity = state.activities.firstOrNull { it.id == doc.itineraryItemId },
                                tripStart = tripStart,
                                kept = doc.id in state.kept,
                                opening = state.opening == doc.id,
                                canDelete = !state.fromDevice && (isOwner || doc.uploadedByMemberId == myMemberId),
                                onOpen = { onOpen(doc) },
                                onDelete = { onDelete(doc) },
                            )
                        }
                    }
                }
                if (state.receipts.isNotEmpty()) {
                    Column {
                        Text(stringResource(Res.string.documents_group_receipts), style = MaterialTheme.typography.titleSmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
                        state.receipts.forEach { doc ->
                            ReceiptDocumentRow(
                                doc = doc,
                                expense = doc.expenseId?.let { state.expenses[it] },
                                tripStart = tripStart,
                                opening = state.opening == doc.id,
                                canDelete = !state.fromDevice && (isOwner || doc.uploadedByMemberId == myMemberId),
                                loader = loader,
                                onOpen = { onOpen(doc) },
                                onDelete = { onDelete(doc) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The trip's document storage: "128 MB dari 200 MB terpakai", a 6 dp bar, red above 90 % with "Hampir penuh". */
@Composable
private fun QuotaBar(used: Long, limit: Long) {
    val scheme = MaterialTheme.colorScheme
    val fraction = if (limit > 0) (used.toFloat() / limit).coerceIn(0f, 1f) else 0f
    val almostFull = fraction > 0.9f
    Column(
        Modifier.fillMaxWidth().clip(IrisShape.md).background(scheme.surfaceContainer).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Symbol(Symbols.cloudDone, size = 18.dp, tint = if (almostFull) scheme.error else scheme.onSurfaceVariant)
            Text(
                stringResource(Res.string.documents_used, sizeText(used), sizeText(limit)) + if (almostFull) " · " + stringResource(Res.string.documents_quota_almost_full) else "",
                style = MaterialTheme.typography.labelLarge, color = if (almostFull) scheme.error else scheme.onSurface,
            )
        }
        androidx.compose.material3.LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(IrisShape.sm),
            color = if (almostFull) scheme.error else scheme.primary,
            trackColor = scheme.outlineVariant,
        )
    }
}

/** Screen 4: a row of the group "Struk": thumbnail 48×60, the place, "Gambar · Hari 2 · Rp845.000 · Septian". */
@Composable
private fun ReceiptDocumentRow(
    doc: TripDocumentDto,
    expense: ExpenseDto?,
    tripStart: LocalDate?,
    opening: Boolean,
    canDelete: Boolean,
    loader: PhotoLoader,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val bitmap = rememberThumbnail(doc.id) { loader.load(ViewerPhoto(doc.id, doc.title, doc.title)) }
    val day = expense?.let { e ->
        val date = e.spentAt.toLocalDateTime(TimeZone.currentSystemDefault()).date
        tripStart?.daysUntil(date)?.plus(1)?.takeIf { it >= 1 }
    }
    val money = expense?.let { formatMoney(it.amountMinor, it.currency) }
    val type = stringResource(Res.string.document_type_image)
    val dayText = day?.let { stringResource(Res.string.itinerary_day_chip, it) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 68.dp).clip(IrisShape.md).clickable(role = Role.Button, onClick = onOpen).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PhotoThumbnail(bitmap, 48.dp, 60.dp, IrisShape.md)
        Column(Modifier.weight(1f)) {
            Text(doc.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(type, dayText, money, doc.uploadedByName.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (opening) CircularProgressIndicator(Modifier.size(20.dp))
        // the receipt icon: this photo is linked to an expense
        Symbol(Symbols.receiptLong, size = 20.dp, tint = scheme.primary)
        if (canDelete) {
            Box {
                Box(Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button) { menu = true }, contentAlignment = Alignment.Center) {
                    Symbol(Symbols.moreVert, tint = scheme.onSurfaceVariant, contentDescription = stringResource(Res.string.document_more, doc.title))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.document_delete), color = scheme.error) },
                        leadingIcon = { Symbol(Symbols.delete, tint = scheme.error) },
                        onClick = { menu = false; confirm = true },
                    )
                }
            }
        }
    }
    if (confirm) {
        // the expense goes with its photo, and the balances move: say which and how much
        ConfirmDialog(
            title = stringResource(Res.string.receipt_delete_both_title),
            body = stringResource(Res.string.receipt_delete_both_body, expense?.note?.takeIf { it.isNotBlank() } ?: doc.title, money ?: ""),
            confirmLabel = stringResource(Res.string.receipt_delete_both_confirm),
            dismissLabel = stringResource(Res.string.action_cancel),
            onConfirm = { confirm = false; onDelete() },
            onDismiss = { confirm = false },
            destructive = true,
            symbol = Symbols.delete,
        )
    }
}

val DocumentCategory.label: StringResource
    get() = when (this) {
        DocumentCategory.TRAVEL -> Res.string.document_category_TRAVEL
        DocumentCategory.STAY -> Res.string.document_category_STAY
        DocumentCategory.ACTIVITY -> Res.string.document_category_ACTIVITY
        DocumentCategory.OTHER -> Res.string.document_category_OTHER
    }

/** The icon subset has no confirmation_number or description: a ticket and a receipt stand in. */
val DocumentCategory.symbol: String
    get() = when (this) {
        DocumentCategory.TRAVEL -> Symbols.flight
        DocumentCategory.STAY -> Symbols.hotel
        DocumentCategory.ACTIVITY -> Symbols.localActivity
        DocumentCategory.OTHER -> Symbols.receiptLong
    }

/** "1,2 MB", "340 KB". */
fun sizeText(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> {
        val tenths = (bytes * 10 + 512 * 1024) / (1024 * 1024)
        if (tenths % 10 == 0L) "${tenths / 10} MB" else "${tenths / 10},${tenths % 10} MB"
    }
    else -> "${(bytes + 1023) / 1024} KB"
}

/** "Hari 1, 06.10" for a document that belongs to an activity. */
@Composable
private fun activityText(item: ItineraryItemDto, tripStart: LocalDate?): String? {
    val day = item.dayDate ?: return null
    val number = tripStart?.daysUntil(day)?.plus(1) ?: return null
    val time = item.startTime?.let { "${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}" }
    return listOfNotNull(stringResource(Res.string.itinerary_day_chip, number), time).joinToString(", ")
}

@Composable
private fun DocumentRow(
    doc: TripDocumentDto,
    activity: ItineraryItemDto?,
    tripStart: LocalDate?,
    kept: Boolean,
    opening: Boolean,
    canDelete: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val type = stringResource(
        when {
            doc.kind == DocumentKind.LINK -> Res.string.document_type_link
            doc.mime == "application/pdf" -> Res.string.document_type_pdf
            else -> Res.string.document_type_image
        },
    )
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(IrisShape.md).clickable(role = Role.Button, onClick = onOpen).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(IrisShape.md).background(IrisTheme.accents.blue), contentAlignment = Alignment.Center) {
            Symbol(if (doc.kind == DocumentKind.LINK) Symbols.link else doc.category.symbol, tint = IrisTheme.accents.onAccent)
        }
        Column(Modifier.weight(1f)) {
            Text(doc.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MemberAvatar(doc.uploadedByName, doc.uploadedByMemberId, size = 18.dp)
                Text(
                    listOfNotNull(type, activity?.let { activityText(it, tripStart) }, doc.uploadedByName.takeIf { it.isNotBlank() }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when {
            opening -> CircularProgressIndicator(Modifier.size(20.dp))
            kept -> Symbol(Symbols.cloudDone, size = 20.dp, tint = scheme.primary, contentDescription = stringResource(Res.string.document_offline))
        }
        if (canDelete) {
            Box {
                Box(Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button) { menu = true }, contentAlignment = Alignment.Center) {
                    Symbol(Symbols.moreVert, tint = scheme.onSurfaceVariant, contentDescription = stringResource(Res.string.document_more, doc.title))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.document_delete), color = scheme.error) },
                        leadingIcon = { Symbol(Symbols.delete, tint = scheme.error) },
                        onClick = { menu = false; confirm = true },
                    )
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            text = { Text(stringResource(Res.string.document_delete_confirm, doc.title)) },
            confirmButton = { TextButton(onClick = { confirm = false; onDelete() }) { Text(stringResource(Res.string.document_delete), color = scheme.error) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

/** "Tambah dokumen": a picked file (PDF or image) or a link, a name, a group and optionally an activity. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddDocumentSheet(
    activities: List<ItineraryItemDto>,
    tripStart: LocalDate?,
    saving: Boolean,
    /** Non-null while the picked file uploads: the form makes way for the surfing capybara. */
    uploadProgress: Float?,
    problem: String?,
    onSave: (title: String, category: DocumentCategory, itineraryItemId: String?, file: PickedFile?, link: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var file by remember { mutableStateOf<PickedFile?>(null) }
    var link by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(DocumentCategory.TRAVEL) }
    var activity by remember { mutableStateOf<String?>(null) }
    var pickActivity by remember { mutableStateOf(false) }
    val picker = rememberFilePickerLauncher(type = FileKitType.File(listOf("pdf", "jpg", "jpeg", "png", "webp", "heic"))) { picked ->
        if (picked != null) scope.launch {
            val name = picked.name
            file = PickedFile(name, picked.readBytes())
            if (title.isBlank()) title = name.substringBeforeLast('.').take(80)
        }
    }
    AdaptiveSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.document_upload_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Box(Modifier.size(48.dp).clickable(role = Role.Button, onClick = onDismiss), contentAlignment = Alignment.Center) {
                Symbol(Symbols.close, contentDescription = stringResource(Res.string.itinerary_close))
            }
        }
        if (uploadProgress != null) {
            // the capybara surfs the progress bar while the file goes up (design "Selancar", owner 31 Oct 2026)
            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SurfUploadProgress(
                    progress = uploadProgress,
                    label = stringResource(Res.string.document_uploading),
                    description = stringResource(Res.string.document_uploading_description),
                    modifier = Modifier.fillMaxWidth(),
                )
                file?.let {
                    Text(
                        "${it.name} · ${sizeText(it.bytes.size.toLong())}",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            return@AdaptiveSheet
        }
        Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            OutlinedButton(onClick = { picker.launch() }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Symbol(Symbols.cloudUpload)
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(file?.let { "${it.name} · ${sizeText(it.bytes.size.toLong())}" } ?: stringResource(Res.string.document_pick_file), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(Res.string.document_pick_file_hint), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
            if (file == null) {
                IrisTextField(label = stringResource(Res.string.document_or_link), value = link, onValueChange = { link = it.trim() }, placeholder = stringResource(Res.string.document_link_label))
            }
            IrisTextField(label = stringResource(Res.string.document_title_label), value = title, onValueChange = { title = it.take(80) })
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.document_category_label), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DocumentCategory.entries.forEach { option ->
                        val on = option == category
                        Row(
                            Modifier.heightIn(min = 40.dp).clip(CircleShape)
                                .background(if (on) scheme.primaryContainer else scheme.surface)
                                .border(1.dp, if (on) scheme.primary else scheme.outlineVariant, CircleShape)
                                .clickable(role = Role.RadioButton) { category = option }
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Symbol(option.symbol, size = 18.dp, tint = if (on) scheme.onPrimaryContainer else scheme.onSurfaceVariant)
                            Text(stringResource(option.label), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            if (activities.isNotEmpty()) {
                // a full screen, not a dropdown: a long itinerary stays readable (owner, 27 Oct 2026)
                val chosen = activities.firstOrNull { it.id == activity }
                OutlinedButton(onClick = { pickActivity = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(
                        chosen?.let { item -> listOfNotNull(activityText(item, tripStart), item.title).joinToString(" · ") } ?: stringResource(Res.string.document_activity_label),
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    Symbol(Symbols.chevronRight)
                }
            }
            problem?.let { Text(stringResource(problemText(it)), style = MaterialTheme.typography.bodySmall, color = scheme.error) }
            LoadingButton(
                onClick = { onSave(title, category, activity, file, link) },
                loading = saving,
                enabled = title.isNotBlank() && (file != null || link.isNotBlank()),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(Res.string.document_save)) }
        }
        if (pickActivity) {
            ActivityPicker(
                activities = activities,
                tripStart = tripStart,
                selectedId = activity,
                onPick = { activity = it; pickActivity = false },
                onDismiss = { pickActivity = false },
            )
        }
    }
}

@Preview
@Composable
private fun DocumentsContentPreview() = IrisPreview {
    val at = Instant.parse("2026-10-26T00:00:00Z")
    val docs = listOf(
        TripDocumentDto("1", "t", "Tiket pesawat CGK–DPS", DocumentCategory.TRAVEL, DocumentKind.FILE, fileName = "tiket.pdf", mime = "application/pdf", sizeBytes = 240_000, uploadedByMemberId = "s", uploadedByName = "Septian", createdAt = at),
        TripDocumentDto("2", "t", "Konfirmasi villa Seminyak", DocumentCategory.STAY, DocumentKind.LINK, url = "https://contoh.id", uploadedByMemberId = "d", uploadedByName = "Dinda", createdAt = at),
        TripDocumentDto("3", "t", "E-tiket Tari Kecak (5 orang)", DocumentCategory.ACTIVITY, DocumentKind.FILE, fileName = "kecak.jpg", mime = "image/jpeg", sizeBytes = 1_200_000, uploadedByMemberId = "r", uploadedByName = "Raka", createdAt = at),
    )
    DocumentsContent(
        state = DocumentsUiState(loading = false, documents = docs, usedBytes = 1_440_000, kept = setOf("1")),
        tripTitle = "Bali 4 hari", tripStart = LocalDate(2026, 11, 20), keepsFiles = true, myMemberId = "s", isOwner = true,
        onBack = {}, onOpen = {}, onDelete = {},
    )
}
