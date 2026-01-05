package com.bw

import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bw.ui.theme.BWTheme


data class ExerciseRow(val id: Long, val name: String, val reps: MutableList<String>)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BWTheme {
                Scaffold { innerPadding ->
                    GridScreen(Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)

@Composable
private fun GridScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dao = remember { AppDb.get(context).workoutDao() }
    val scope = rememberCoroutineScope()

    val setCount = 3
    var nextId by remember { mutableLongStateOf(1L) }
    val rows = remember { mutableStateListOf<ExerciseRow>() }

    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val dateKey = remember(selectedDate) { selectedDate.toString() } // "YYYY-MM-DD"

    var status by remember { mutableStateOf("") }

    var showTotals by remember { mutableStateOf(false) }
    var allTimeTotal by remember { mutableIntStateOf(0) }
    val totalsByExercise = remember { mutableStateListOf<ExerciseTotal>() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Load when date changes
    LaunchedEffect(dateKey) {
        status = "Loading $dateKey..."
        val loaded = withContext(Dispatchers.IO) { loadRowsForDate(dao, dateKey, setCount) }
        rows.clear()
        rows.addAll(loaded)
        nextId = (rows.size + 1).toLong()
        status = "Loaded $dateKey."
    }

    fun saveDay() {
        scope.launch {
            status = "Saving $dateKey..."
            withContext(Dispatchers.IO) { saveRowsForDate(dao, dateKey, rows, setCount) }
            status = "Saved $dateKey."
        }
    }

    fun refreshTotals() {
        scope.launch {
            val (tAll, tByEx) = withContext(Dispatchers.IO) {
                dao.getAllTimeTotalReps() to dao.getTotalsByExercise()
            }
            allTimeTotal = tAll
            totalsByExercise.clear()
            totalsByExercise.addAll(tByEx)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // Date row
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

        Spacer(Modifier.height(8.dp))

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

        Spacer(Modifier.height(10.dp))

        // ---- Your existing grid UI below ----

        val nameW = 88.dp
        val cellW = 66.dp
        val totalW = 66.dp

        Row(verticalAlignment = Alignment.CenterVertically) {
            HeaderCell("Exercise", nameW)
            for (s in 1..setCount) HeaderCell("Set $s", cellW)
            HeaderCell("Total", totalW)
        }
        HorizontalDivider()

        rows.forEachIndexed { rIdx, row ->
            key(row.id) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ExerciseNameCell(
                        value = row.name,
                        onValueChange = { newName -> rows[rIdx] = row.copy(name = newName) },
                        width = nameW
                    )

                    for (sIdx in 0 until setCount) {
                        RepsCell(
                            value = row.reps[sIdx],
                            onValueChange = { v ->
                                val filtered = v.filter(Char::isDigit)
                                val newReps = row.reps.toMutableList().also { it[sIdx] = filtered }
                                rows[rIdx] = row.copy(reps = newReps)
                            },
                            width = cellW
                        )
                    }

                    val total = row.reps.sumOf { it.toIntOrNull() ?: 0 }
                    BodyCellText(
                        text = total.toString(),
                        width = totalW,
                        align = Alignment.Center,
                        bold = true
                    )
                }
                HorizontalDivider()
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

        OutlinedButton(
            onClick = {
                refreshTotals()
                showTotals = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Totals")
        }

        if (showTotals) {
            ModalBottomSheet(
                onDismissRequest = { showTotals = false },
                sheetState = sheetState
            ) {
                TotalsSheetContent(
                    allTimeTotal = allTimeTotal,
                    totalsByExercise = totalsByExercise,
                    onClose = { showTotals = false }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        val grandTotal = rows.sumOf { it.reps.sumOf { v -> v.toIntOrNull() ?: 0 } }
        Text("Total reps (all exercises): $grandTotal", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun HeaderCell(text: String, width: Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .padding(6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun BodyCellText(
    text: String,
    width: Dp,
    align: Alignment = Alignment.CenterStart,
    bold: Boolean = false
) {
    Box(
        modifier = Modifier
            .width(width)
            .padding(6.dp),
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

@Composable
private fun ExerciseNameCell(
    value: String,
    onValueChange: (String) -> Unit,
    width: Dp
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .width(width)
            .padding(6.dp),
        maxLines = 2,              // <-- allow wrapping
        minLines = 1,
        singleLine = false,        // <-- must be false
        textStyle = LocalTextStyle.current.copy(
            textAlign = TextAlign.Start
        )
    )
}

@Composable
private fun RepsCell(
    value: String,
    onValueChange: (String) -> Unit,
    width: Dp
) {
    Box(
        modifier = Modifier
            .width(width)
            .padding(6.dp)
    ) {
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

@Composable
private fun TotalsSheetContent(
    allTimeTotal: Int,
    totalsByExercise: List<ExerciseTotal>,
    onClose: () -> Unit
) {
    // If you do not want scrolling, keep the list short (e.g. top 12).
    val top = totalsByExercise.take(12)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text("Totals", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))

        Text(
            "All-time total reps: $allTimeTotal",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        Text("By exercise (top ${top.size})", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        top.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    item.exerciseName,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                Text(
                    item.totalReps.toString(),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(72.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Close")
        }

        Spacer(Modifier.height(12.dp))
    }
}


