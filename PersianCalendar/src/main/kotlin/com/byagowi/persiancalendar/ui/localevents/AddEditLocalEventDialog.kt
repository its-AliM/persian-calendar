package com.byagowi.persiancalendar.ui.localevents

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.byagowi.persiancalendar.data.LocalEventsRepository
import com.byagowi.persiancalendar.entities.Calendar
import com.byagowi.persiancalendar.entities.Jdn
import com.byagowi.persiancalendar.entities.LocalEvent
import com.byagowi.persiancalendar.entities.LocalEventCategory
import com.byagowi.persiancalendar.entities.LocalEventRecurrence
import com.byagowi.persiancalendar.global.language
import com.byagowi.persiancalendar.global.numeral
import com.byagowi.persiancalendar.global.yearAwareMonthsNames
import com.byagowi.persiancalendar.shared.generated.resources.Res
import com.byagowi.persiancalendar.shared.generated.resources.add_local_event
import com.byagowi.persiancalendar.shared.generated.resources.all_day
import com.byagowi.persiancalendar.shared.generated.resources.cancel
import com.byagowi.persiancalendar.shared.generated.resources.category
import com.byagowi.persiancalendar.shared.generated.resources.category_anniversary
import com.byagowi.persiancalendar.shared.generated.resources.category_birthday
import com.byagowi.persiancalendar.shared.generated.resources.category_event
import com.byagowi.persiancalendar.shared.generated.resources.category_reminder
import com.byagowi.persiancalendar.shared.generated.resources.delete_local_event
import com.byagowi.persiancalendar.shared.generated.resources.delete_local_event_confirmation
import com.byagowi.persiancalendar.shared.generated.resources.description
import com.byagowi.persiancalendar.shared.generated.resources.edit_local_event
import com.byagowi.persiancalendar.shared.generated.resources.recurrence
import com.byagowi.persiancalendar.shared.generated.resources.recurrence_monthly
import com.byagowi.persiancalendar.shared.generated.resources.recurrence_none
import com.byagowi.persiancalendar.shared.generated.resources.recurrence_yearly
import com.byagowi.persiancalendar.shared.generated.resources.reminder
import com.byagowi.persiancalendar.shared.generated.resources.reminder_15_mins
import com.byagowi.persiancalendar.shared.generated.resources.reminder_1_day
import com.byagowi.persiancalendar.shared.generated.resources.reminder_1_hour
import com.byagowi.persiancalendar.shared.generated.resources.reminder_2_days
import com.byagowi.persiancalendar.shared.generated.resources.reminder_at_time
import com.byagowi.persiancalendar.shared.generated.resources.reminder_none
import com.byagowi.persiancalendar.shared.generated.resources.save
import com.byagowi.persiancalendar.shared.generated.resources.time
import com.byagowi.persiancalendar.shared.generated.resources.title
import com.byagowi.persiancalendar.shared.generated.resources.title_required
import com.byagowi.persiancalendar.shared.generated.resources.year_unknown
import com.byagowi.persiancalendar.ui.common.AppDialog
import com.byagowi.persiancalendar.ui.common.NumberPicker
import com.byagowi.persiancalendar.ui.icons.material.filled.deleteIcon
import io.github.persiancalendar.calendar.PersianDate
import org.jetbrains.compose.resources.stringResource
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditLocalEventDialog(
    initialDate: PersianDate,
    initialEvent: LocalEvent? = null,
    onDismissRequest: () -> Unit,
    onEventSaved: (LocalEvent) -> Unit,
    onEventDeleted: ((LocalEvent) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var titleText by rememberSaveable { mutableStateOf(initialEvent?.title ?: "") }
    var descriptionText by rememberSaveable { mutableStateOf(initialEvent?.description ?: "") }
    var isTitleError by rememberSaveable { mutableStateOf(false) }

    var category by rememberSaveable { mutableStateOf(initialEvent?.category ?: LocalEventCategory.EVENT) }
    var recurrence by rememberSaveable { mutableStateOf(initialEvent?.recurrence ?: LocalEventRecurrence.NONE) }

    var isYearUnknown by rememberSaveable {
        mutableStateOf(initialEvent?.let { it.jalaliYear == null && it.recurrence != LocalEventRecurrence.NONE } ?: false)
    }

    var selectedYear by rememberSaveable {
        mutableIntStateOf(initialEvent?.jalaliYear ?: initialDate.year)
    }
    var selectedMonth by rememberSaveable {
        mutableIntStateOf(initialEvent?.jalaliMonth ?: initialDate.month)
    }
    var selectedDay by rememberSaveable {
        mutableIntStateOf(initialEvent?.jalaliDay ?: initialDate.dayOfMonth)
    }

    var isAllDay by rememberSaveable { mutableStateOf(initialEvent?.isAllDay ?: true) }
    var selectedHour by rememberSaveable { mutableIntStateOf(initialEvent?.hour ?: 9) }
    var selectedMinute by rememberSaveable { mutableIntStateOf(initialEvent?.minute ?: 0) }

    var selectedReminderOffset by rememberSaveable {
        mutableStateOf(initialEvent?.reminderOffsetMinutes)
    }

    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }

    val pendingConfirms = remember { mutableStateListOf<() -> Unit>() }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Permission grant handled cleanly */ }

    val maxDayForMonth by remember {
        derivedStateOf {
            val effYear = if (isYearUnknown) 1403 else selectedYear // 1403 is a leap year allowing 30
            Calendar.SHAMSI.getMonthLength(effYear, selectedMonth)
        }
    }

    // Adjust day if month changed and day exceeds month length
    if (selectedDay > maxDayForMonth) {
        selectedDay = maxDayForMonth
    }

    if (showDeleteConfirm && initialEvent != null) {
        AppDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(Res.string.delete_local_event)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDismissRequest()
                        LocalEventsRepository.deleteEvent(context, initialEvent.id)
                        onEventDeleted?.invoke(initialEvent)
                    },
                ) {
                    Text(stringResource(Res.string.delete_local_event), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        ) {
            Text(stringResource(Res.string.delete_local_event_confirmation))
        }
    }

    AppDialog(
        modifier = modifier,
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                stringResource(
                    if (initialEvent == null) Res.string.add_local_event
                    else Res.string.edit_local_event,
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    pendingConfirms.forEach { it() }
                    val trimmed = titleText.trim()
                    if (trimmed.isEmpty()) {
                        isTitleError = true
                        return@TextButton
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && selectedReminderOffset != null) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }

                    val now = System.currentTimeMillis()
                    val eventToSave = LocalEvent(
                        id = initialEvent?.id ?: UUID.randomUUID().toString(),
                        title = trimmed,
                        description = descriptionText.trim(),
                        jalaliYear = if (isYearUnknown && recurrence != LocalEventRecurrence.NONE) null else selectedYear,
                        jalaliMonth = selectedMonth,
                        jalaliDay = selectedDay,
                        isAllDay = isAllDay,
                        hour = if (!isAllDay) selectedHour else null,
                        minute = if (!isAllDay) selectedMinute else null,
                        category = category,
                        recurrence = recurrence,
                        reminderOffsetMinutes = selectedReminderOffset,
                        createdAtEpochMillis = initialEvent?.createdAtEpochMillis ?: now,
                        updatedAtEpochMillis = now,
                    )

                    if (initialEvent == null) {
                        LocalEventsRepository.addEvent(context, eventToSave)
                    } else {
                        LocalEventsRepository.updateEvent(context, eventToSave)
                    }

                    onDismissRequest()
                    onEventSaved(eventToSave)
                },
            ) {
                Text(stringResource(Res.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(Res.string.cancel))
            }
        },
        neutralButton = if (initialEvent != null) {
            {
                TextButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        deleteIcon,
                        contentDescription = stringResource(Res.string.delete_local_event),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(Res.string.delete_local_event),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        } else null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Title Field
            OutlinedTextField(
                value = titleText,
                onValueChange = {
                    titleText = it
                    if (isTitleError && it.trim().isNotEmpty()) isTitleError = false
                },
                label = { Text(stringResource(Res.string.title)) },
                isError = isTitleError,
                supportingText = if (isTitleError) {
                    { Text(stringResource(Res.string.title_required)) }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Category Chips
            Text(stringResource(Res.string.category), style = MaterialTheme.typography.labelMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val categories = listOf(
                    LocalEventCategory.EVENT to Res.string.category_event,
                    LocalEventCategory.BIRTHDAY to Res.string.category_birthday,
                    LocalEventCategory.ANNIVERSARY to Res.string.category_anniversary,
                    LocalEventCategory.REMINDER to Res.string.category_reminder,
                )
                categories.forEach { (cat, titleRes) ->
                    val selected = category == cat
                    FilterChip(
                        selected = selected,
                        onClick = {
                            category = cat
                            if (cat == LocalEventCategory.BIRTHDAY || cat == LocalEventCategory.ANNIVERSARY) {
                                if (recurrence == LocalEventRecurrence.NONE) {
                                    recurrence = LocalEventRecurrence.YEARLY_JALALI
                                }
                            }
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

            // Recurrence Chips
            Text(stringResource(Res.string.recurrence), style = MaterialTheme.typography.labelMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val recurrences = listOf(
                    LocalEventRecurrence.NONE to Res.string.recurrence_none,
                    LocalEventRecurrence.YEARLY_JALALI to Res.string.recurrence_yearly,
                    LocalEventRecurrence.MONTHLY_JALALI to Res.string.recurrence_monthly,
                )
                recurrences.forEach { (rec, titleRes) ->
                    FilterChip(
                        selected = recurrence == rec,
                        onClick = {
                            recurrence = rec
                            if (rec == LocalEventRecurrence.NONE) isYearUnknown = false
                        },
                        label = { Text(stringResource(titleRes)) },
                    )
                }
            }

            // Unknown Year toggle (only when recurring)
            AnimatedVisibility(visible = recurrence != LocalEventRecurrence.NONE) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isYearUnknown = !isYearUnknown },
                ) {
                    Checkbox(
                        checked = isYearUnknown,
                        onCheckedChange = { isYearUnknown = it },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.year_unknown), style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Jalali Date Pickers (Day, Month, Year)
            val monthsNames = remember(selectedYear) {
                yearAwareMonthsNames(PersianDate(selectedYear, 1, 1))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Day
                NumberPicker(
                    value = selectedDay,
                    range = 1..maxDayForMonth,
                    pendingConfirms = pendingConfirms,
                    modifier = Modifier.weight(1f),
                    label = { numeral.format(it) },
                    onValueChange = { selectedDay = it },
                )

                // Month
                NumberPicker(
                    value = selectedMonth,
                    range = 1..12,
                    pendingConfirms = pendingConfirms,
                    modifier = Modifier.weight(1.5f),
                    label = { m ->
                        val name = monthsNames.getOrNull(m - 1).orEmpty()
                        language.inParentheses.format(name, numeral.format(m))
                    },
                    onValueChange = { selectedMonth = it },
                )

                // Year (hidden if isYearUnknown)
                if (!isYearUnknown || recurrence == LocalEventRecurrence.NONE) {
                    NumberPicker(
                        value = selectedYear,
                        range = 1300..1500,
                        pendingConfirms = pendingConfirms,
                        modifier = Modifier.weight(1.2f),
                        label = { numeral.format(it) },
                        onValueChange = { selectedYear = it },
                    )
                }
            }

            // All-Day Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(Res.string.all_day), style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = isAllDay,
                    onCheckedChange = { isAllDay = it },
                )
            }

            // Time Pickers (Hour & Minute when not all-day)
            AnimatedVisibility(visible = !isAllDay) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(Res.string.time), style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        NumberPicker(
                            value = selectedHour,
                            range = 0..23,
                            pendingConfirms = pendingConfirms,
                            modifier = Modifier.weight(1f),
                            label = { "%02d".format(java.util.Locale.ENGLISH, it).let(numeral::format) },
                            onValueChange = { selectedHour = it },
                        )
                        Text(":", style = MaterialTheme.typography.headlineMedium)
                        NumberPicker(
                            value = selectedMinute,
                            range = 0..59,
                            pendingConfirms = pendingConfirms,
                            modifier = Modifier.weight(1f),
                            label = { "%02d".format(java.util.Locale.ENGLISH, it).let(numeral::format) },
                            onValueChange = { selectedMinute = it },
                        )
                    }
                }
            }

            // Reminder Offset Chips
            Text(stringResource(Res.string.reminder), style = MaterialTheme.typography.labelMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val offsets = listOf(
                    null to Res.string.reminder_none,
                    0 to Res.string.reminder_at_time,
                    15 to Res.string.reminder_15_mins,
                    60 to Res.string.reminder_1_hour,
                    1440 to Res.string.reminder_1_day,
                    2880 to Res.string.reminder_2_days,
                )
                offsets.forEach { (offset, titleRes) ->
                    FilterChip(
                        selected = selectedReminderOffset == offset,
                        onClick = { selectedReminderOffset = offset },
                        label = { Text(stringResource(titleRes)) },
                    )
                }
            }

            // Description / Notes Field
            OutlinedTextField(
                value = descriptionText,
                onValueChange = { descriptionText = it },
                label = { Text(stringResource(Res.string.description)) },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}
