package com.example.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import com.example.util.DateFormatUtils
import java.time.LocalDate
import java.util.Locale

/** Tracks draft validity separately from canonical values, including optional fields. */
internal class DateFieldValidation {
    private val fields = mutableStateMapOf<String, Boolean>()
    val isValid: Boolean get() = fields.values.all { it }
    fun isValidFor(keys: List<String>): Boolean = keys.all { fields[it] != false }
    fun update(key: String, valid: Boolean) { fields[key] = valid }
}

@Composable internal fun rememberDateFieldValidation(): DateFieldValidation = remember { DateFieldValidation() }

/** Emits only valid canonical ISO dates, or "" when explicitly cleared. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDateField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
    initialDate: LocalDate? = null,
    testTag: String? = null,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
    onValidityChange: (Boolean) -> Unit = {}
) {
    var text by rememberSaveable { mutableStateOf(DateFormatUtils.safeFormat(value)) }
    var observedValue by rememberSaveable { mutableStateOf(value) }
    var touched by rememberSaveable { mutableStateOf(false) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(value) {
        if (value != observedValue) {
            text = DateFormatUtils.safeFormat(value)
            observedValue = value
            touched = false
        }
    }
    fun allowed(date: LocalDate): Boolean = (minDate == null || !date.isBefore(minDate)) &&
        (maxDate == null || !date.isAfter(maxDate))
    val parsed = DateFormatUtils.parseGerman(text)
    val valid = if (text.isBlank()) !required else parsed != null && allowed(parsed)
    SideEffect { onValidityChange(valid) }
    val error = when {
        text.isBlank() && required -> "Bitte ein Datum auswählen."
        parsed == null -> DateFormatUtils.INPUT_ERROR
        minDate != null && parsed.isBefore(minDate) -> "Das Datum darf nicht vor dem ${DateFormatUtils.format(minDate)} liegen."
        maxDate != null && parsed.isAfter(maxDate) -> "Das Datum darf nicht nach dem ${DateFormatUtils.format(maxDate)} liegen."
        else -> null
    }
    val currentCallback by rememberUpdatedState(onValueChange)
    val currentValidityCallback by rememberUpdatedState(onValidityChange)
    val fieldModifier = modifier.focusRequester(focus).then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
        .pointerInput(enabled, readOnly) {
            awaitEachGesture {
                awaitFirstDown(pass = PointerEventPass.Initial)
                if (waitForUpOrCancellation(pass = PointerEventPass.Initial) != null && enabled && !readOnly) {
                    keyboard?.hide()
                    showPicker = true
                }
            }
        }
    OutlinedTextField(
        value = text,
        onValueChange = { draft ->
            text = draft
            touched = true
            val date = DateFormatUtils.parseGerman(draft)
            val draftValid = if (draft.isBlank()) !required else date != null && allowed(date)
            currentValidityCallback(draftValid)
            if (draft.isBlank() || (date != null && allowed(date))) {
                val iso = date?.toString().orEmpty()
                observedValue = iso
                currentCallback(iso)
            }
        },
        label = { Text(label) },
        placeholder = { Text("TT.MM.JJJJ") },
        enabled = enabled,
        readOnly = readOnly,
        singleLine = true,
        shape = Ui2.controlShape,
        isError = isError || (!valid && (touched || text.isNotBlank())),
        supportingText = if (!valid && (touched || text.isNotBlank())) ({ Text(error ?: DateFormatUtils.INPUT_ERROR) }) else supportingText,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        trailingIcon = {
            IconButton(onClick = { keyboard?.hide(); showPicker = true }, enabled = enabled && !readOnly,
                modifier = Modifier.testTag("${testTag ?: label}_calendar")) {
                Icon(Icons.Default.CalendarMonth, contentDescription = "Datum auswählen")
            }
        },
        modifier = fieldModifier
    )
    if (showPicker && enabled && !readOnly) {
        val selected = parsed?.takeIf(::allowed)
        val initial = selected ?: initialDate?.takeIf(::allowed)
        val shown = initial ?: LocalDate.now().let { today ->
            when { minDate != null && today.isBefore(minDate) -> minDate
                maxDate != null && today.isAfter(maxDate) -> maxDate
                else -> today }
        }
        val selectable = remember(minDate, maxDate) { object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = allowed(DateFormatUtils.fromPickerMillis(utcTimeMillis))
            override fun isSelectableYear(year: Int): Boolean = year >= (minDate?.year ?: 1) && year <= (maxDate?.year ?: 9999)
        } }
        val state = remember(minDate, maxDate) { DatePickerState(
            locale = Locale.GERMANY,
            initialSelectedDateMillis = initial?.let(DateFormatUtils::toPickerMillis),
            initialDisplayedMonthMillis = DateFormatUtils.toPickerMillis(shown),
            yearRange = minOf(1900, shown.year, minDate?.year ?: 1900)..maxOf(2100, shown.year, maxDate?.year ?: 2100),
            initialDisplayMode = DisplayMode.Picker,
            selectableDates = selectable
        ) }
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = { TextButton(enabled = state.selectedDateMillis != null, onClick = {
                state.selectedDateMillis?.let { millis ->
                    val date = DateFormatUtils.fromPickerMillis(millis)
                    if (allowed(date)) {
                        text = DateFormatUtils.format(date)
                        observedValue = date.toString()
                        touched = true
                        currentValidityCallback(true)
                        currentCallback(date.toString())
                    }
                }
                showPicker = false
            }, modifier = Modifier.testTag("app_date_apply")) { Text("Übernehmen") } },
            dismissButton = { Column {
                TextButton(onClick = { showPicker = false }, modifier = Modifier.testTag("app_date_cancel")) { Text("Abbrechen") }
                TextButton(onClick = { showPicker = false; focus.requestFocus(); keyboard?.show() },
                    modifier = Modifier.testTag("app_date_manual")) { Text("Manuell eingeben") }
            } }
        ) {
            DatePicker(state, showModeToggle = false,
                title = { Text(label) },
                headline = { Text(state.selectedDateMillis?.let { DateFormatUtils.format(DateFormatUtils.fromPickerMillis(it)) } ?: "Datum auswählen") },
                modifier = Modifier.fillMaxWidth().testTag("app_date_picker"))
        }
    }
}
