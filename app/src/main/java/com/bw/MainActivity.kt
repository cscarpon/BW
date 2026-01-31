package com.bw

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bw.ui.theme.BWTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

data class ExerciseRow(val id: Long, val name: String, val reps: MutableList<String>)

enum class WorkoutPage() {
    HOME(),
    TRACK(),
    TOTALS()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BWTheme {
                WorkoutApp()
            }
        }
    }
}

@Composable
private fun WorkoutApp() {
    val context = LocalContext.current
    val dao = remember { AppDb.get(context).workoutDao() }
    val scope = rememberCoroutineScope()

    var currentPage by rememberSaveable { mutableStateOf(WorkoutPage.HOME) }
    val activeDates = remember { mutableStateListOf<String>() }
    val exerciseSuggestions = remember { mutableStateListOf<String>() }

    fun refreshActiveDates() {
        scope.launch {
            val dates = withContext(Dispatchers.IO) { dao.getActiveDates() }
            activeDates.clear()
            activeDates.addAll(dates)
        }
    }

    fun refreshExerciseSuggestions() {
        scope.launch {
            val names = withContext(Dispatchers.IO) { dao.getAllExerciseNames() }
            val displayMap = buildExerciseDisplayMap(names)
            exerciseSuggestions.clear()
            exerciseSuggestions.addAll(displayMap.values.sorted())
        }
    }

    LaunchedEffect(Unit) {
        refreshActiveDates()
        refreshExerciseSuggestions()
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentPage == WorkoutPage.HOME,
                    onClick = { currentPage = WorkoutPage.HOME },
                    icon = { Text("🏠") },
                    label = { Text("Home") }
                )
                NavigationBarItem(
                    selected = currentPage == WorkoutPage.TRACK,
                    onClick = { currentPage = WorkoutPage.TRACK },
                    icon = { Text("🏋️") },
                    label = { Text("Track") }
                )
                NavigationBarItem(
                    selected = currentPage == WorkoutPage.TOTALS,
                    onClick = { WorkoutPage.TOTALS },
                    icon = { Text("📊") },
                    label = { Text("Totals") }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentPage) {
                WorkoutPage.HOME -> HomeScreen(
                    activeDates = activeDates,
                    onRefresh = { refreshActiveDates() }
                )

                WorkoutPage.TRACK -> TrackWorkoutScreen(
                    dao = dao,
                    exerciseSuggestions = exerciseSuggestions,
                    onSave = {
                        refreshActiveDates()
                        refreshExerciseSuggestions()
                    }
                )

                WorkoutPage.TOTALS -> TotalsScreen(dao = dao)
            }
        }
    }
}

@Composable
private fun HomeScreen(
    activeDates: List<String>,
    onRefresh: () -> Unit
) {
    var month by rememberSaveable { mutableStateOf(YearMonth.now()) }
    val activeDatesSet = remember(activeDates) { activeDates.toSet() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Welcome back",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Your workout streaks this month",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = { month = month.minusMonths(1) }) { Text("◀") }
                    Text(
                        text = "${month.month.name.lowercase().replaceFirstChar { it.titlecase() }} ${month.year}",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(onClick = { month = month.plusMonths(1) }) { Text("▶") }
                }

                Spacer(Modifier.height(12.dp))

                GitHubCalendar(
                    month = month,
                    activeDates = activeDatesSet
                )

                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Refresh calendar")
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Active days",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "You have logged workouts on ${activeDates.size} days so far.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun GitHubCalendar(
    month: YearMonth,
    activeDates: Set<String>
) {
    val firstDay = month.atDay(1)
    val leadingBlanks = firstDay.dayOfWeek.value - 1 // Monday=1
    val totalDays = month.lengthOfMonth()
    val totalCells = leadingBlanks + totalDays
    val rows = (totalCells / 7) + if (totalCells % 7 == 0) 0 else 1

    val cellSize = 32.dp
    val gap = 6.dp

    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        // FIX 1: header uses same spacing/box sizing as grid rows (no SpaceBetween)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap)
        ) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                Box(
                    modifier = Modifier.size(cellSize),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        repeat(rows) { rowIndex ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                repeat(7) { colIndex ->
                    val cellIndex = rowIndex * 7 + colIndex
                    if (cellIndex < leadingBlanks || cellIndex >= leadingBlanks + totalDays) {
                        Box(
                            modifier = Modifier
                                .size(cellSize)
                                .background(Color.Transparent)
                        )
                    } else {
                        val dayNumber = cellIndex - leadingBlanks + 1
                        val date = month.atDay(dayNumber)
                        val isActive = activeDates.contains(date.toString())
                        Box(
                            modifier = Modifier
                                .size(cellSize)
                                .background(
                                    color = if (isActive) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(6.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dayNumber.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isActive) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackWorkoutScreen(
    dao: WorkoutDao,
    exerciseSuggestions: List<String>,
    onSave: () -> Unit
) {
    val scope = rememberCoroutineScope()

    val setCount = 3
    var nextId by remember { mutableLongStateOf(1L) }
    val rows = remember { mutableStateListOf<ExerciseRow>() }

    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val dateKey = remember(selectedDate) { selectedDate.toString() }

    var status by remember { mutableStateOf("") }

    LaunchedEffect(dateKey) {
        status = "Loading $dateKey..."
        val loaded = withContext(Dispatchers.IO) { loadRowsForDate(dao, dateKey, setCount) }
        rows.clear()
        rows.addAll(loaded)
        nextId = (rows.size + 1).toLong()
        status = ""
    }

    fun saveDay() {
        scope.launch {
            status = "Saving $dateKey..."
            withContext(Dispatchers.IO) { saveRowsForDate(dao, dateKey, rows, setCount) }
            status = "Saved."
            onSave()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Track workout",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(onClick = { selectedDate = selectedDate.minusDays(1) }) { Text("◀") }
                    Text(
                        text = dateKey,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge
                    )
                    OutlinedButton(onClick = { selectedDate = selectedDate.plusDays(1) }) { Text("▶") }
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { saveDay() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save workout")
                }

                if (status.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(status, style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Exercises",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))

        // FIX 3: Use widthIn for Total column + slightly reduce Exercise column weight
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeaderCell("Exercise", Modifier.weight(1.3f))
            for (s in 1..setCount) HeaderCell("Set $s", Modifier.weight(1f))
            HeaderCell("Total", Modifier.widthIn(min = 56.dp, max = 72.dp))
        }
        HorizontalDivider()

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            itemsIndexed(rows, key = { _, row -> row.id }) { rIdx, row ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ExerciseNameCell(
                            value = row.name,
                            onValueChange = { newName -> rows[rIdx] = row.copy(name = newName) },
                            suggestions = exerciseSuggestions,
                            modifier = Modifier.weight(1.3f) // match header weight
                        )

                        for (sIdx in 0 until setCount) {
                            RepsCell(
                                value = row.reps[sIdx],
                                onValueChange = { v ->
                                    val filtered = v.filter(Char::isDigit)
                                    val newReps = row.reps.toMutableList().also { it[sIdx] = filtered }
                                    rows[rIdx] = row.copy(reps = newReps)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        val total = row.reps.sumOf { it.toIntOrNull() ?: 0 }
                        BodyCellText(
                            text = total.toString(),
                            modifier = Modifier.widthIn(min = 56.dp, max = 72.dp),
                            align = Alignment.Center,
                            bold = true
                        )
                    }
                    HorizontalDivider()
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = { rows.add(ExerciseRow(nextId++, "", MutableList(setCount) { "" })) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("+ Exercise")
        }

        Spacer(Modifier.height(10.dp))

        val grandTotal = rows.sumOf { it.reps.sumOf { v -> v.toIntOrNull() ?: 0 } }
        Text(
            "Total reps (all exercises): $grandTotal",
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun TotalsScreen(dao: WorkoutDao) {
    val scope = rememberCoroutineScope()
    var allTimeTotal by remember { mutableIntStateOf(0) }
    val totalsByExercise = remember { mutableStateListOf<ExerciseTotal>() }

    fun refreshTotals() {
        scope.launch {
            val sets = withContext(Dispatchers.IO) { dao.getAllSets() }
            val grouped = mutableMapOf<String, Int>()
            val groupNames = mutableMapOf<String, MutableList<String>>()

            sets.forEach { set ->
                val key = normalizeExerciseName(set.exerciseName)
                grouped[key] = (grouped[key] ?: 0) + set.reps
                groupNames.getOrPut(key) { mutableListOf() }.add(set.exerciseName)
            }

            val totals = grouped.map { (key, total) ->
                val displayName = groupNames[key]?.let { chooseDisplayName(it) }
                    ?: key.replaceFirstChar { it.titlecase() }
                ExerciseTotal(displayName, total)
            }.sortedByDescending { it.totalReps }

            allTimeTotal = grouped.values.sum()
            totalsByExercise.clear()
            totalsByExercise.addAll(totals.take(10))
        }
    }

    LaunchedEffect(Unit) { refreshTotals() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Totals",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "All-time total reps",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    allTimeTotal.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Top exercises",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                totalsByExercise.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}. ${item.exerciseName}",
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        Text(
                            item.totalReps.toString(),
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.End,
                            modifier = Modifier.widthIn(min = 56.dp, max = 72.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { refreshTotals() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Refresh totals")
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Box(
        modifier = modifier.padding(6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun BodyCellText(
    text: String,
    modifier: Modifier,
    align: Alignment = Alignment.CenterStart,
    bold: Boolean = false
) {
    Box(
        modifier = modifier.padding(6.dp),
        contentAlignment = align
    ) {
        Text(
            text = text,
            fontWeight = if (bold) FontWeight.SemiBold else null,
            textAlign = when (align) {
                Alignment.Center, Alignment.CenterEnd -> TextAlign.Center
                else -> TextAlign.Start
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseNameCell(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    modifier: Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val filteredSuggestions = remember(value, suggestions) {
        if (value.isBlank()) suggestions
        else suggestions.filter { it.contains(value, ignoreCase = true) }
    }

    ExposedDropdownMenuBox(
        expanded = expanded && filteredSuggestions.isNotEmpty(),
        onExpandedChange = { expanded = !expanded }
    ) {
        // FIX 2: force single line; use new menuAnchor overload
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier
                .padding(6.dp)
                .menuAnchor(MenuAnchorType.PrimaryEditable),
            singleLine = true,
            maxLines = 1,
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Start),
            placeholder = { Text("Exercise name", maxLines = 1) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )

        ExposedDropdownMenu(
            expanded = expanded && filteredSuggestions.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            filteredSuggestions.take(8).forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion, maxLines = 1) },
                    onClick = {
                        onValueChange(suggestion)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun RepsCell(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier
) {
    Box(modifier = modifier.padding(6.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private suspend fun loadRowsForDate(
    dao: WorkoutDao,
    date: String,
    setCount: Int
): List<ExerciseRow> {
    val exercises = dao.getExercises(date)
    val sets = dao.getSets(date)

    val repMap = HashMap<String, MutableList<String>>()
    for (e in exercises) repMap[e.name] = MutableList(setCount) { "" }

    for (s in sets) {
        val list = repMap.getOrPut(s.exerciseName) { MutableList(setCount) { "" } }
        if (s.setIndex in 0 until setCount) list[s.setIndex] = s.reps.toString()
    }

    return exercises.mapIndexed { idx, e ->
        ExerciseRow(
            id = (idx + 1).toLong(),
            name = e.name,
            reps = repMap[e.name] ?: MutableList(setCount) { "" }
        )
    }
}

private suspend fun saveRowsForDate(
    dao: WorkoutDao,
    date: String,
    rows: List<ExerciseRow>,
    setCount: Int
) {
    val cleaned = rows
        .map { it.copy(name = it.name.trim()) }
        .filter { it.name.isNotEmpty() }

    val exercises = cleaned.mapIndexed { idx, r ->
        WorkoutExerciseEntity(date = date, name = r.name, position = idx)
    }

    val sets = ArrayList<WorkoutSetEntity>()
    for (r in cleaned) {
        for (i in 0 until setCount) {
            val reps = r.reps.getOrNull(i)?.toIntOrNull() ?: 0
            if (reps > 0) {
                sets.add(
                    WorkoutSetEntity(
                        date = date,
                        exerciseName = r.name,
                        setIndex = i,
                        reps = reps
                    )
                )
            }
        }
    }

    dao.replaceDay(date, exercises, sets)
}

private fun normalizeExerciseName(name: String): String {
    val cleaned = name.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
    return if (cleaned.endsWith("s") && cleaned.length > 3) cleaned.dropLast(1) else cleaned
}

private fun buildExerciseDisplayMap(names: List<String>): Map<String, String> {
    val grouped = names.groupBy { normalizeExerciseName(it) }
    return grouped.mapValues { (_, list) -> chooseDisplayName(list) }
}

private fun chooseDisplayName(names: List<String>): String {
    val counts = names.groupingBy { it.trim() }.eachCount()
    return counts.entries
        .sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.value }
                .thenBy { it.key.length }
                .thenBy { it.key }
        )
        .firstOrNull()
        ?.key
        ?.trim()
        ?.replaceFirstChar { it.titlecase() }
        ?: ""
}