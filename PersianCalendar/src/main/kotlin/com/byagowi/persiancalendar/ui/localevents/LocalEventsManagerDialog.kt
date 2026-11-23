package com.byagowi.persiancalendar.ui.localevents

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.byagowi.persiancalendar.data.LocalEventsRepository
import com.byagowi.persiancalendar.entities.Clock
import com.byagowi.persiancalendar.entities.Jdn
import com.byagowi.persiancalendar.entities.LocalEvent
import com.byagowi.persiancalendar.entities.LocalEventCategory
import com.byagowi.persiancalendar.entities.LocalEventRecurrence
import com.byagowi.persiancalendar.entities.daysUntilNextOccurrence
import com.byagowi.persiancalendar.entities.elapsedYears
import com.byagowi.persiancalendar.entities.nextOccurrenceDate
import com.byagowi.persiancalendar.global.language
import com.byagowi.persiancalendar.global.numeral
import com.byagowi.persiancalendar.global.yearAwareMonthsNames
import com.byagowi.persiancalendar.shared.generated.resources.Res
import com.byagowi.persiancalendar.shared.generated.resources.add_local_event
import com.byagowi.persiancalendar.shared.generated.resources.all_day
import com.byagowi.persiancalendar.shared.generated.resources.category_anniversary
import com.byagowi.persiancalendar.shared.generated.resources.category_birthday
import com.byagowi.persiancalendar.shared.generated.resources.category_event
import com.byagowi.persiancalendar.shared.generated.resources.category_reminder
import com.byagowi.persiancalendar.shared.generated.resources.close
import com.byagowi.persiancalendar.shared.generated.resources.countdown_n_days
import com.byagowi.persiancalendar.shared.generated.resources.countdown_today
import com.byagowi.persiancalendar.shared.generated.resources.countdown_tomorrow
import com.byagowi.persiancalendar.shared.generated.resources.manage_local_events
import com.byagowi.persiancalendar.shared.generated.resources.no_local_events
import com.byagowi.persiancalendar.shared.generated.resources.search_in_events
import com.byagowi.persiancalendar.ui.common.AppDialog
import com.byagowi.persiancalendar.ui.icons.material.filled.addIcon
import com.byagowi.persiancalendar.ui.icons.material.filled.editIcon
import io.github.persiancalendar.calendar.PersianDate
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LocalEventsManagerDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val eventsList by LocalEventsRepository.events.collectAsState()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedCategoryFilter by rememberSaveable { mutableStateOf<LocalEventCategory?>(null) }

    var editingEvent by remember { mutableStateOf<LocalEvent?>(null) }
    var isAddingEvent by remember { mutableStateOf(false) }

    val todayJdn = remember { Jdn.today() }
    val todayPersian = remember(todayJdn) { todayJdn.toPersianDate() }

    // Sort events by upcoming occurrence
    val filteredSortedEvents = remember(eventsList, searchQuery, selectedCategoryFilter, todayJdn) {
        val query = searchQuery.trim().lowercase()
        eventsList
            .filter { event ->
                (selectedCategoryFilter == null || event.category == selectedCategoryFilter) &&
                    (query.isEmpty() || event.title.lowercase().contains(query) || event.description.lowercase().contains(query))
            }
            .sortedBy { event ->
                daysUntilNextOccurrence(event, todayJdn) ?: Int.MAX_VALUE
            }
    }

    if (isAddingEvent) {
        AddEditLocalEventDialog(
            initialDate = todayPersian,
            onDismissRequest = { isAddingEvent = false },
            onEventSaved = { isAddingEvent = false },
        )
    }

    editingEvent?.let { event ->
        AddEditLocalEventDialog(
            initialDate = todayPersian,
            initialEvent = event,
            onDismissRequest = { editingEvent = null },
            onEventSaved = { editingEvent = null },
            onEventDeleted = { editingEvent = null },
        )
    }

    AppDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(Res.string.manage_local_events)) },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Res.string.close))
            }
        },
        neutralButton = {
            TextButton(onClick = { isAddingEvent = true }) {
                Icon(
                    addIcon,
                    contentDescription = stringResource(Res.string.add_local_event),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(stringResource(Res.string.add_local_event))
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(stringResource(Res.string.search_in_events)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Category filter chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilterChip(
                    selected = selectedCategoryFilter == null,
                    onClick = { selectedCategoryFilter = null },
                    label = { Text(if (language.isPersianOrDari) "همه" else "All") },
                )
                val categories = listOf(
                    LocalEventCategory.EVENT to Res.string.category_event,
                    LocalEventCategory.BIRTHDAY to Res.string.category_birthday,
                    LocalEventCategory.ANNIVERSARY to Res.string.category_anniversary,
                    LocalEventCategory.REMINDER to Res.string.category_reminder,
                )
                categories.forEach { (cat, titleRes) ->
                    FilterChip(
                        selected = selectedCategoryFilter == cat,
                        onClick = {
                            selectedCategoryFilter = if (selectedCategoryFilter == cat) null else cat
                        },
                        leadingIcon = {
                            Icon(
                                categoryIcon(cat),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        label = { Text(stringResource(titleRes)) },
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            if (filteredSortedEvents.isEmpty()) {
                Text(
                    text = stringResource(Res.string.no_local_events),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    filteredSortedEvents.forEach { event ->
                        val nextDate = nextOccurrenceDate(event, todayPersian)
                        val daysUntil = daysUntilNextOccurrence(event, todayJdn)
                        val monthsNames = remember(nextDate?.year ?: 1403) {
                            yearAwareMonthsNames(PersianDate(nextDate?.year ?: 1403, 1, 1))
                        }

                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editingEvent = event },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(12.dp),
                            ) {
                                // Category Icon
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                ) {
                                    Icon(
                                        categoryIcon(event.category),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }

                                Spacer(Modifier.width(12.dp))

                                // Event details
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = event.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )

                                        val age = nextDate?.year?.let { elapsedYears(event, it) }
                                        if (age != null) {
                                            Spacer(Modifier.width(6.dp))
                                            Surface(
                                                shape = MaterialTheme.shapes.small,
                                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                            ) {
                                                Text(
                                                    text = if (event.category == LocalEventCategory.BIRTHDAY) {
                                                        if (language.isPersianOrDari) "تولد ${numeral.format(age)} سالگی"
                                                        else "$age years"
                                                    } else {
                                                        if (language.isPersianOrDari) "سالگرد ${numeral.format(age)} سالگی"
                                                        else "$age years"
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                )
                                            }
                                        }
                                    }

                                    // Subtitle: Date + Time
                                    val monthName = nextDate?.month?.let { monthsNames.getOrNull(it - 1) }.orEmpty()
                                    val dateString = if (nextDate != null) {
                                        "${numeral.format(nextDate.dayOfMonth)} $monthName" +
                                            if (event.jalaliYear != null) " ${numeral.format(nextDate.year)}" else ""
                                    } else ""

                                    val timeString = if (event.isAllDay) {
                                        stringResource(Res.string.all_day)
                                    } else {
                                        val h = event.hour ?: 0
                                        val m = event.minute ?: 0
                                        Clock(h + m / 60.0).toFormattedString(printAmPm = true)
                                    }

                                    Text(
                                        text = "$dateString • $timeString",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    )

                                    if (event.description.isNotBlank()) {
                                        Text(
                                            text = event.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        )
                                    }
                                }

                                // Countdown badge
                                if (daysUntil != null) {
                                    Spacer(Modifier.width(8.dp))
                                    val badgeColor = when (daysUntil) {
                                        0 -> MaterialTheme.colorScheme.primary
                                        1 -> MaterialTheme.colorScheme.secondary
                                        else -> MaterialTheme.colorScheme.outlineVariant
                                    }
                                    val badgeTextColor = when (daysUntil) {
                                        0 -> MaterialTheme.colorScheme.onPrimary
                                        1 -> MaterialTheme.colorScheme.onSecondary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    val badgeText = when (daysUntil) {
                                        0 -> stringResource(Res.string.countdown_today)
                                        1 -> stringResource(Res.string.countdown_tomorrow)
                                        else -> stringResource(Res.string.countdown_n_days, numeral.format(daysUntil))
                                    }

                                    Surface(
                                        shape = MaterialTheme.shapes.small,
                                        color = badgeColor,
                                    ) {
                                        Text(
                                            text = badgeText,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = badgeTextColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
