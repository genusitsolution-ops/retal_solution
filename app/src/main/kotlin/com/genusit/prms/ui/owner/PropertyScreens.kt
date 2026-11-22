package com.genusit.prms.ui.owner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.genusit.prms.R
import com.genusit.prms.domain.model.Building
import com.genusit.prms.domain.model.BuildingForm
import com.genusit.prms.domain.model.BuildingInput
import com.genusit.prms.domain.model.PropertySummary
import com.genusit.prms.domain.model.PropertyTypes
import com.genusit.prms.domain.model.SelectedProperty
import com.genusit.prms.domain.repository.PropertyScope
import com.genusit.prms.domain.usecase.DeleteBuildingUseCase
import com.genusit.prms.domain.usecase.GetBuildingUseCase
import com.genusit.prms.domain.usecase.ListPropertiesUseCase
import com.genusit.prms.domain.usecase.SavePropertyUseCase
import com.genusit.prms.domain.usecase.SelectPropertyUseCase
import com.genusit.prms.domain.util.Money
import com.genusit.prms.ui.common.ActionError
import com.genusit.prms.ui.common.ActionState
import com.genusit.prms.ui.common.AppCard
import com.genusit.prms.ui.common.BaseViewModel
import com.genusit.prms.ui.common.ConfirmDialog
import com.genusit.prms.ui.common.EmptyState
import com.genusit.prms.ui.common.ErrorBox
import com.genusit.prms.ui.common.EventEffect
import com.genusit.prms.ui.common.FormField
import com.genusit.prms.ui.common.LoadingBox
import com.genusit.prms.ui.common.ScreenEvent
import com.genusit.prms.ui.common.ScreenScaffold
import com.genusit.prms.ui.common.SessionExpiryHandler
import com.genusit.prms.ui.common.StateContent
import com.genusit.prms.ui.common.SubmitButton
import com.genusit.prms.ui.common.UiError
import com.genusit.prms.ui.common.errorText
import com.genusit.prms.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/*
 * 1.17 — Multi-property management for owners: the property header on Home (which property, its rooms and
 * occupancy), the property picker (search + pages from the server), the property-wise room summary for
 * All Properties, and the Add / Edit Property screen. Every figure comes from the server.
 */

/* ═════════════════════════ shared pieces ═════════════════════════ */

/** Bar colour by occupancy: green from 85 %, blue from 50 %, amber below. */
fun occupancyColor(pct: Int): Color = when {
    pct >= 85 -> Palette.Success
    pct >= 50 -> Palette.Teal
    else -> Palette.Warning
}

@Composable
fun OccupancyBar(pct: Int, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.prop_occupancy_pct, pct)
    val p = pct.coerceIn(0, 100) / 100f
    Box(modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(6.dp)).background(Palette.Line).semantics { contentDescription = label }) {
        if (p > 0f) Box(Modifier.fillMaxWidth(p).height(10.dp).clip(RoundedCornerShape(6.dp)).background(occupancyColor(pct)))
    }
}

/** Total / Occupied / Vacant / Occupancy % in four columns. */
@Composable
fun OccupancyFigures(total: Int, occupied: Int, vacant: Int, pct: Int) {
    Row(Modifier.fillMaxWidth()) {
        @Composable
        fun cell(value: String, label: String, color: Color) = Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        cell(total.toString(), stringResource(R.string.prop_total_rooms), Palette.Ink)
        cell(occupied.toString(), stringResource(R.string.prop_occupied), Palette.Success)
        cell(vacant.toString(), stringResource(R.string.prop_vacant), Palette.Warning)
        cell("$pct%", stringResource(R.string.prop_occupancy), occupancyColor(pct))
    }
}

/**
 * Top of the owner's Home: which property is open (with one big "Change" button) and the four things an owner
 * checks first — occupied rooms, vacant rooms, rent collected this month, rent pending (overdue part in red).
 * Each figure opens the list behind it. Every number comes from the server.
 */
@Composable
fun PropertyHeaderCard(
    title: String, typeLabel: String?, summary: PropertySummary?, onChange: () -> Unit, onRooms: () -> Unit,
    onOccupied: () -> Unit = onRooms, onVacant: () -> Unit = onRooms, onCollected: () -> Unit = {}, onPending: () -> Unit = {},
    onTenants: () -> Unit = {}, onComplaints: () -> Unit = {},
) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Palette.TealSoft), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Apartment, null, tint = Palette.Teal)
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(stringResource(R.string.prop_label).uppercase(), style = MaterialTheme.typography.labelSmall, color = Palette.Teal, fontWeight = FontWeight.Bold)
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Palette.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!typeLabel.isNullOrBlank()) Text(typeLabel, style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                }
                Button(onClick = onChange, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                    Icon(Icons.Filled.SwapHoriz, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.prop_change))
                }
            }
            if (summary != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FigureTile(stringResource(R.string.prop_occupied_rooms), "${summary.occupiedRooms}", "/ ${summary.totalRooms}", Palette.Success,
                        stringResource(R.string.prop_pct_occupied, summary.occupancyPct), summary.occupancyPct, occupancyColor(summary.occupancyPct), onOccupied, Modifier.weight(1f))
                    FigureTile(stringResource(R.string.prop_vacant_rooms), "${summary.vacantRooms}", null, Palette.Warning,
                        if (summary.maintenanceRooms > 0) stringResource(R.string.prop_maintenance_n, summary.maintenanceRooms) else stringResource(R.string.prop_ready_to_let),
                        null, Palette.Warning, onVacant, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FigureTile(stringResource(R.string.prop_collected_month), Money.format(summary.collected, decimals = 0), null, Palette.Teal,
                        stringResource(R.string.prop_billed_amount, Money.format(summary.monthBilled, decimals = 0)),
                        if (summary.monthBilled > 0) summary.collectionPct else null, Palette.Success, onCollected, Modifier.weight(1f))
                    FigureTile(stringResource(R.string.prop_pending_rent), Money.format(summary.pendingRent, decimals = 0), null, Palette.Danger,
                        if (summary.overdueAmount > 0) stringResource(R.string.prop_overdue_amount, Money.format(summary.overdueAmount, decimals = 0)) else stringResource(R.string.prop_nothing_overdue),
                        null, Palette.Danger, onPending, Modifier.weight(1f), subColor = if (summary.overdueAmount > 0) Palette.Danger else Palette.Muted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallFact(stringResource(R.string.prop_active_tenants), summary.activeTenants, onTenants)
                    SmallFact(stringResource(R.string.open_queries), summary.openComplaints, onComplaints)
                }
            }
        }
    }
}

/** One big figure with its label, a short line under it and (optionally) a progress bar; tapping opens its list. */
@Composable
private fun FigureTile(
    label: String, value: String, suffix: String?, accent: Color, sub: String, barPct: Int?, barColor: Color,
    onClick: () -> Unit, modifier: Modifier = Modifier, subColor: Color = Palette.Muted,
) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.08f)).clickable(onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (suffix != null) { Spacer(Modifier.width(4.dp)); Text(suffix, style = MaterialTheme.typography.titleSmall, color = Palette.Muted, modifier = Modifier.padding(bottom = 2.dp)) }
        }
        if (barPct != null) OccupancyBarColored(barPct, barColor)
        Text(sub, style = MaterialTheme.typography.labelMedium, color = subColor, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SmallFact(label: String, value: Int, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Palette.Line.copy(alpha = 0.5f)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
        Spacer(Modifier.width(6.dp))
        Text(value.toString(), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Palette.Ink)
    }
}

@Composable
private fun OccupancyBarColored(pct: Int, color: Color) {
    val p = pct.coerceIn(0, 100) / 100f
    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(6.dp)).background(Palette.Line)) {
        if (p > 0f) Box(Modifier.fillMaxWidth(p).height(8.dp).clip(RoundedCornerShape(6.dp)).background(color))
    }
}

/** One property in a list: name, city · type, "x of y rooms occupied", occupancy bar. */
@Composable
fun PropertyRow(b: Building, selected: Boolean, onClick: () -> Unit, onEdit: (() -> Unit)? = null) {
    AppCard(onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(b.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Palette.Ink,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (selected) { Spacer(Modifier.width(6.dp)); Icon(Icons.Filled.CheckCircle, stringResource(R.string.prop_selected), tint = Palette.Success, modifier = Modifier.size(18.dp)) }
                    }
                    Text(listOfNotNull(b.city.takeIf { it.isNotBlank() }, b.typeLabel, if (!b.active) stringResource(R.string.prop_inactive) else null).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium, color = Palette.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${b.occupancyPct}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = occupancyColor(b.occupancyPct))
                    Text(stringResource(R.string.prop_occ_of, b.occupied, b.rooms), style = MaterialTheme.typography.labelSmall, color = Palette.Muted)
                }
                if (onEdit != null) IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, stringResource(R.string.prop_edit), tint = Palette.Brand) }
            }
            OccupancyBar(b.occupancyPct)
            if (b.pendingDues > 0 || b.monthCollected > 0) Row {
                Text(stringResource(R.string.prop_collected_short, Money.format(b.monthCollected, decimals = 0)), style = MaterialTheme.typography.labelMedium, color = Palette.Success, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.prop_pending_short, Money.format(b.pendingDues, decimals = 0)), style = MaterialTheme.typography.labelMedium,
                    color = if (b.pendingDues > 0) Palette.Danger else Palette.Muted, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Home with All Properties: Total / Occupied / Vacant / % of the first properties, and "View all". */
@Composable
fun PropertyWiseCard(cards: List<Building>, total: Int, onOpen: (Building) -> Unit, onViewAll: () -> Unit) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.prop_wise_rooms), style = MaterialTheme.typography.titleMedium, color = Palette.Ink, modifier = Modifier.weight(1f))
                TextButton(onClick = onViewAll) { Text(stringResource(R.string.prop_view_all_n, total)) }
            }
            cards.forEach { b ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onOpen(b) }.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(b.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${b.occupancyPct}%", fontWeight = FontWeight.ExtraBold, color = occupancyColor(b.occupancyPct))
                    }
                    Row {
                        Text(stringResource(R.string.prop_wise_line, b.rooms, b.occupied, b.available), style = MaterialTheme.typography.labelMedium, color = Palette.Muted, modifier = Modifier.weight(1f))
                        if (b.pendingDues > 0) Text(stringResource(R.string.prop_pending_short, Money.format(b.pendingDues, decimals = 0)), style = MaterialTheme.typography.labelMedium, color = Palette.Danger, fontWeight = FontWeight.SemiBold)
                    }
                    OccupancyBar(b.occupancyPct)
                }
            }
        }
    }
}

/* ═════════════════════════ picker ═════════════════════════ */

data class PropertyListState(
    val query: String = "",
    val items: List<Building> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = false,
    val total: Int = 0,
    val loading: Boolean = true,
    val error: UiError? = null,
)

@HiltViewModel
class PropertyPickerViewModel @Inject constructor(
    private val list: ListPropertiesUseCase,
    private val selectProperty: SelectPropertyUseCase,
    scope: PropertyScope,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    private val _state = MutableStateFlow(PropertyListState())
    val state: StateFlow<PropertyListState> = _state.asStateFlow()
    val selected: StateFlow<SelectedProperty?> = scope.selected
    val selectAction = action<Unit>()
    private var job: Job? = null

    init {
        load(reset = true)
        // a property added or edited elsewhere appears at once
        viewModelScope.launch { expiry.dataChanges.changes.collect { load(reset = true, debounce = false) } }
    }

    fun search(q: String) {
        _state.value = _state.value.copy(query = q)
        load(reset = true, debounce = true)
    }

    fun more() { if (_state.value.hasMore && !_state.value.loading) load(reset = false) }

    fun retry() = load(reset = _state.value.items.isEmpty())

    private fun load(reset: Boolean, debounce: Boolean = false) {
        job?.cancel()
        val s = _state.value
        val next = if (reset) 1 else s.page + 1
        _state.value = s.copy(loading = true, error = null)
        job = viewModelScope.launch {
            if (debounce) delay(300)
            try {
                val p = list(_state.value.query, next)
                val cur = _state.value
                _state.value = cur.copy(items = if (reset) p.items else cur.items + p.items, page = p.page, hasMore = p.hasMore,
                    total = p.total, loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                expiry.onError(e)
                _state.value = _state.value.copy(loading = false, error = UiError.from(e))
            }
        }
    }

    fun select(id: Int, label: String) = selectAction.run(onSuccess = { sendEvent(ScreenEvent.Saved) }) { selectProperty(id, label) }
}

/**
 * "Change Property": All Properties on top, then the owner's properties with search and pages loaded from the
 * server (fast with hundreds of properties). Tapping one opens it everywhere in the app.
 */
@Composable
fun PropertyPickerScreen(onBack: () -> Unit, onSelected: () -> Unit, addProperty: () -> Unit, editProperty: (Int) -> Unit) {
    val model: PropertyPickerViewModel = hiltViewModel()
    val s by model.state.collectAsStateWithLifecycle()
    val sel by model.selected.collectAsStateWithLifecycle()
    val act by model.selectAction.state.collectAsStateWithLifecycle()
    EventEffect(model) { if (it == ScreenEvent.Saved) onSelected() }
    val allLabel = stringResource(R.string.prop_all)
    ScreenScaffold(stringResource(R.string.prop_choose), onBack = onBack, showProperty = false, actions = {
        IconButton(onClick = addProperty) { Icon(Icons.Filled.Add, stringResource(R.string.prop_add)) }
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedTextField(
                    value = s.query, onValueChange = { model.search(it.take(100)) }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    placeholder = { Text(stringResource(R.string.prop_search_hint)) },
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
                )
            }
            item { ActionError(act) }
            if (s.query.isBlank()) item {
                AppCard(onClick = { model.select(0, allLabel) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(allLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Palette.Ink)
                            Text(stringResource(R.string.prop_all_sub, s.total), style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                        }
                        if ((sel?.id ?: 0) == 0) Icon(Icons.Filled.CheckCircle, stringResource(R.string.prop_selected), tint = Palette.Success)
                    }
                }
            }
            items(s.items, key = { it.id }) { b ->
                PropertyRow(b, selected = sel?.id == b.id, onClick = { model.select(b.id, b.title) }, onEdit = { editProperty(b.id) })
            }
            when {
                s.loading -> item { LoadingBox(Modifier.fillMaxWidth().height(80.dp)) }
                s.error != null -> item { ErrorBox(s.error!!, model::retry) }
                s.items.isEmpty() -> item { EmptyState(stringResource(if (s.query.isBlank()) R.string.prop_none else R.string.prop_not_found), Icons.Filled.Apartment) }
                s.hasMore -> item {
                    // the next page loads when the end of the list is reached
                    LaunchedEffect(s.page) { model.more() }
                    OutlinedButton(onClick = model::more, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.prop_show_more)) }
                }
            }
            item {
                Button(onClick = addProperty, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.prop_add), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/* ═════════════════════════ add / edit ═════════════════════════ */

@HiltViewModel
class PropertyFormViewModel @Inject constructor(
    handle: SavedStateHandle,
    getProperty: GetBuildingUseCase,
    private val save: SavePropertyUseCase,
    private val remove: DeleteBuildingUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val id: Int = handle.get<Int>("id") ?: 0
    val property = loadable(autoLoad = id > 0) { getProperty(id) }
    val saveAction = action<Unit>()
    val deleteAction = action<Unit>()

    fun save(input: BuildingInput) = saveAction.run(onSuccess = { sendEvent(ScreenEvent.Saved) }) { save(id, input) }
    fun delete() = deleteAction.run(onSuccess = { sendEvent(ScreenEvent.Deleted) }) { remove(id) }
}

@Composable
fun PropertyFormScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val model: PropertyFormViewModel = hiltViewModel()
    EventEffect(model) { if (it == ScreenEvent.Saved || it == ScreenEvent.Deleted) onDone() }
    val title = stringResource(if (model.id > 0) R.string.prop_edit else R.string.prop_add)
    ScreenScaffold(title, onBack = onBack, showProperty = false) { pad ->
        if (model.id > 0) {
            val st by model.property.state.collectAsStateWithLifecycle()
            StateContent(st, model.property::refresh, Modifier.padding(pad)) { b -> PropertyForm(model, b) }
        } else Box(Modifier.padding(pad)) { PropertyForm(model, null) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PropertyForm(model: PropertyFormViewModel, b: Building?) {
    var name by rememberSaveable { mutableStateOf(b?.name.orEmpty()) }
    var city by rememberSaveable { mutableStateOf(b?.city.orEmpty()) }
    var type by rememberSaveable { mutableStateOf(b?.type ?: "other") }
    var area by rememberSaveable { mutableStateOf(b?.area.orEmpty()) }
    var address by rememberSaveable { mutableStateOf(b?.address.orEmpty()) }
    var pin by rememberSaveable { mutableStateOf(b?.pincode.orEmpty()) }
    var desc by rememberSaveable { mutableStateOf(b?.description.orEmpty()) }
    var active by rememberSaveable { mutableStateOf(b?.active ?: true) }
    var more by rememberSaveable { mutableStateOf(listOf(area, address, pin, desc).any { it.isNotBlank() }) }
    var tried by rememberSaveable { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    val act by model.saveAction.state.collectAsStateWithLifecycle()
    val del by model.deleteAction.state.collectAsStateWithLifecycle()
    val typeNames = mapOf("apartment" to R.string.prop_type_apartment, "pg" to R.string.prop_type_pg, "hostel" to R.string.prop_type_hostel,
        "commercial" to R.string.prop_type_commercial, "other" to R.string.prop_type_other)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (b == null) Text(stringResource(R.string.prop_add_hint), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
        FormField(name, { name = it.take(150) }, stringResource(R.string.prop_name) + " *", isError = tried && !BuildingForm.nameOk(name),
            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words)
        FormField(city, { city = it.take(100) }, stringResource(R.string.city) + " *", isError = tried && !BuildingForm.cityOk(city),
            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words)
        Text(stringResource(R.string.prop_type), style = MaterialTheme.typography.labelLarge, color = Palette.Muted)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PropertyTypes.all.forEach { t ->
                FilterChip(selected = type == t, onClick = { type = t }, label = { Text(stringResource(typeNames.getValue(t))) })
            }
        }
        TextButton(onClick = { more = !more }) { Text(stringResource(if (more) R.string.prop_less_details else R.string.prop_more_details)) }
        if (more) {
            FormField(area, { area = it.take(150) }, stringResource(R.string.prop_area))
            FormField(address, { address = it.take(500) }, stringResource(R.string.address), singleLine = false, minLines = 2)
            FormField(pin, { pin = it.filter(Char::isDigit).take(6) }, stringResource(R.string.pincode), isError = tried && !BuildingForm.pincodeOk(pin), keyboard = KeyboardType.Number)
            FormField(desc, { desc = it.take(1000) }, stringResource(R.string.prop_description), singleLine = false, minLines = 2)
        }
        if (b != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.prop_active), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.prop_inactive_hint), style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                }
                Switch(checked = active, onCheckedChange = { active = it })
            }
        }
        ActionError(act)
        SubmitButton(stringResource(if (b == null) R.string.prop_add else R.string.save), act) {
            tried = true
            val input = BuildingInput(name, city, type, area, address, pin, desc, status = if (b != null) (if (active) "active" else "inactive") else null)
            if (BuildingForm.valid(input.copy(name = name.trim(), city = city.trim(), pincode = pin.trim()))) model.save(input)
        }
        if (b != null && b.rooms == 0 && b.tenants == 0) {
            ActionError(del)
            OutlinedButton(onClick = { askDelete = true }, modifier = Modifier.fillMaxWidth(), enabled = del !is ActionState.Running) {
                Text(stringResource(R.string.prop_delete), color = Palette.Danger)
            }
        }
    }
    if (askDelete) ConfirmDialog(stringResource(R.string.prop_delete), stringResource(R.string.prop_delete_confirm), stringResource(R.string.prop_delete),
        onConfirm = { askDelete = false; model.delete() }, onDismiss = { askDelete = false })
}

/* ═════════════════════════ property report ═════════════════════════ */

@HiltViewModel
class PropertyReportViewModel @Inject constructor(
    private val getReport: com.genusit.prms.domain.usecase.GetPropertyReportUseCase,
    private val selectProperty: SelectPropertyUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    private var page = 1
    val report = loadable(live = true) { getReport(page) }
    val selectAction = action<Unit>()
    fun goTo(p: Int) { page = p.coerceAtLeast(1); report.refresh() }
    fun open(b: Building) = selectAction.run { selectProperty(b.id, b.title) }
}

/**
 * Reports → Property: this month's figures of the property that is open; with All Properties one row per
 * property (20 per page) and the combined total. Tapping a property opens it.
 */
@Composable
fun PropertyReportBody(openRooms: (String) -> Unit, openPayments: () -> Unit, openDues: () -> Unit) {
    val model: PropertyReportViewModel = hiltViewModel()
    val st by model.report.state.collectAsStateWithLifecycle()
    StateContent(st, model.report::refresh) { r ->
        val s = r.summary
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(r.property?.title ?: stringResource(R.string.prop_all), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = Palette.Ink)
                Text(stringResource(R.string.prop_report_note), style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
            }
            item {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        @Composable
                        fun line(label: Int, value: String, color: Color = Palette.Ink, onClick: (() -> Unit)? = null) = Row(
                            Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, color = Palette.Muted, modifier = Modifier.weight(1f))
                            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = color)
                        }
                        line(R.string.prop_total_rooms, s.totalRooms.toString(), onClick = { openRooms("") })
                        line(R.string.prop_occupied_rooms, s.occupiedRooms.toString(), Palette.Success) { openRooms("occupied") }
                        line(R.string.prop_vacant_rooms, s.vacantRooms.toString(), Palette.Warning) { openRooms("available") }
                        line(R.string.prop_occupancy, "${s.occupancyPct}%", occupancyColor(s.occupancyPct))
                        OccupancyBar(s.occupancyPct)
                        line(R.string.prop_active_tenants, s.activeTenants.toString())
                        line(R.string.prop_rent_billed, Money.format(s.monthBilled))
                        line(R.string.prop_rent_collected, Money.format(s.collected), Palette.Success, openPayments)
                        line(R.string.prop_pending_rent, Money.format(s.pendingRent), Palette.Warning, openDues)
                        line(R.string.prop_overdue, Money.format(s.overdueAmount), if (s.overdueAmount > 0) Palette.Danger else Palette.Ink, openDues)
                    }
                }
            }
            val rows = r.rows
            if (rows != null && rows.items.isNotEmpty()) {
                item { Text(stringResource(R.string.prop_wise_report), style = MaterialTheme.typography.titleMedium, color = Palette.Ink, modifier = Modifier.padding(top = 6.dp)) }
                items(rows.items, key = { "r" + it.id }) { b ->
                    AppCard(onClick = { model.open(b) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(b.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Palette.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(b.city, style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                                }
                                Text("${b.occupancyPct}%", fontWeight = FontWeight.ExtraBold, color = occupancyColor(b.occupancyPct))
                            }
                            Text(stringResource(R.string.prop_wise_line, b.rooms, b.occupied, b.available) + " · " + stringResource(R.string.prop_tenants_n, b.activeTenants),
                                style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
                            OccupancyBar(b.occupancyPct)
                            Row {
                                Text(stringResource(R.string.prop_collected_short, Money.format(b.monthCollected, decimals = 0)), style = MaterialTheme.typography.labelMedium, color = Palette.Success, modifier = Modifier.weight(1f))
                                Text(stringResource(R.string.prop_pending_short, Money.format(b.pendingDues, decimals = 0)), style = MaterialTheme.typography.labelMedium,
                                    color = if (b.pendingDues > 0) Palette.Danger else Palette.Muted, fontWeight = FontWeight.SemiBold)
                            }
                            if (b.overdue > 0) Text(stringResource(R.string.prop_overdue_amount, Money.format(b.overdue, decimals = 0)), style = MaterialTheme.typography.labelMedium, color = Palette.Danger)
                        }
                    }
                }
                if (rows.pages > 1) item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { model.goTo(rows.page - 1) }, enabled = rows.page > 1) { Text(stringResource(R.string.prop_prev)) }
                        Text("${rows.page} / ${rows.pages}", color = Palette.Muted)
                        OutlinedButton(onClick = { model.goTo(rows.page + 1) }, enabled = rows.hasMore) { Text(stringResource(R.string.prop_next)) }
                    }
                }
            }
        }
    }
}
