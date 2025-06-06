package com.example.appcontrol

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.material3.Button
import androidx.compose.ui.platform.LocalContext
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

class ChartActivity : ComponentActivity() {
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = FirebaseDatabase.getInstance().getReference("air_quality")

        setContent {
            ChartScreen(database, onBackPress = { finish() })
        }
    }
}

@SuppressLint("MutableCollectionMutableState")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(database: DatabaseReference, onBackPress: () -> Unit) {
    val context = LocalContext.current
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var ppmEntries by remember { mutableStateOf(listOf<Entry>()) }
    var tempEntries by remember { mutableStateOf(listOf<Entry>()) }
    var humidityEntries by remember { mutableStateOf(listOf<Entry>()) }
    var dustEntries by remember { mutableStateOf(listOf<Entry>()) }
    var isLoading by remember { mutableStateOf(true) }
    var timeCounter by remember { mutableFloatStateOf(0f) }
    var warningMessage by remember { mutableStateOf("") }
    val savedEntries by remember { mutableStateOf<MutableList<ChartEntryData>>(mutableListOf()) }

    val showDatePickerDialog = {
        val datePicker = DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newDate = LocalDate.of(year, month + 1, dayOfMonth)
                selectedDate = newDate
            },
            selectedDate.year,
            selectedDate.monthValue - 1,
            selectedDate.dayOfMonth
        )
        datePicker.show()
    }

    LaunchedEffect(selectedDate) {
        val formattedDate = selectedDate.toString()
        val dir = File(context.filesDir, "ChartData")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "$formattedDate.json")

        val gson = Gson()
        val type = object : TypeToken<MutableList<ChartEntryData>>() {}.type

        isLoading = true
        warningMessage = ""
        savedEntries.clear()

        try {
            if (file.exists()) {
                val json = file.readText()
                savedEntries.addAll(gson.fromJson(json, type) ?: mutableListOf())
                warningMessage = ""
            } else {
                warningMessage = "❗ Không có dữ liệu cho ngày $formattedDate"
            }

            if (savedEntries.isNotEmpty()) {
                ppmEntries = savedEntries.map { Entry(it.time, it.ppm) }
                tempEntries = savedEntries.map { Entry(it.time, it.temp) }
                humidityEntries = savedEntries.map { Entry(it.time, it.humidity) }
                dustEntries = savedEntries.map { Entry(it.time, it.dust) }
                timeCounter = savedEntries.last().time + 1f
            } else {
                ppmEntries = listOf()
                tempEntries = listOf()
                humidityEntries = listOf()
                dustEntries = listOf()
                timeCounter = 0f
            }
        } catch (e: Exception) {
            Log.e("ChartScreen", "Lỗi đọc JSON: ${e.message}")
            warningMessage = "❗ Lỗi đọc dữ liệu cho ngày $formattedDate"
        }

        isLoading = false
    }

    LaunchedEffect(Unit) {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val ppm = snapshot.child("ppm").getValue(Float::class.java) ?: return
                val temp = snapshot.child("temperature").getValue(Float::class.java) ?: return
                val humidity = snapshot.child("humidity").getValue(Float::class.java) ?: return
                val dust = snapshot.child("dust_density").getValue(Float::class.java) ?: return

                val currentDate = LocalDate.now().toString() // Get current date
                val formattedDate = selectedDate.toString()
                val dir = File(context.filesDir, "ChartData")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "$formattedDate.json")
                val gson = Gson()

                // Create a new entry
                val newEntry = ChartEntryData(timeCounter, ppm, temp, humidity, dust)

                // Only add the new entry if the selected date matches the current date
                if (formattedDate == currentDate) {
                    val isSameAsLast = savedEntries.lastOrNull()?.let {
                        it.ppm == ppm && it.temp == temp && it.humidity == humidity && it.dust == dust
                    } ?: false

                    if (!isSameAsLast) {
                        timeCounter += 1f
                        savedEntries.add(newEntry)
                        if (savedEntries.size > 20) savedEntries.removeAt(0)

                        // Write to the file for the selected date
                        try {
                            file.writeText(gson.toJson(savedEntries))
                        } catch (e: IOException) {
                            Log.e("ChartScreen", "Lỗi ghi file: ${e.message}")
                        }

                        // Update chart data
                        ppmEntries = savedEntries.map { Entry(it.time, it.ppm) }
                        tempEntries = savedEntries.map { Entry(it.time, it.temp) }
                        humidityEntries = savedEntries.map { Entry(it.time, it.humidity) }
                        dustEntries = savedEntries.map { Entry(it.time, it.dust) }

                        // Update warning message based on new data
                        warningMessage = when {
                            ppm > 100 -> "⚠️ Cảnh báo! Chất lượng không khí kém (PPM: $ppm)"
                            dust > 80 -> "⚠️ Cảnh báo! Nồng độ bụi cao (${dust} ug/m³)"
                            else -> warningMessage
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Lỗi: ${error.message}")
            }
        })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Biểu đồ chất lượng không khí") },
                navigationIcon = {
                    IconButton(onClick = onBackPress) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (warningMessage.isNotEmpty()) {
                Text(
                    warningMessage,
                    color = ComposeColor.Red,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(onClick = showDatePickerDialog) {
                Text("Chọn ngày: ${selectedDate.format(DateTimeFormatter.ISO_DATE)}")
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading && savedEntries.isEmpty()) {
                CircularProgressIndicator()
            } else {
                if (ppmEntries.isNotEmpty() || tempEntries.isNotEmpty() || humidityEntries.isNotEmpty() || dustEntries.isNotEmpty()) {
                    ChartItem("Nồng độ PPM", ppmEntries, Color.RED)
                    ChartItem("Nhiệt độ (°C)", tempEntries, Color.BLUE)
                    ChartItem("Độ ẩm (%)", humidityEntries, Color.GREEN)
                    ChartItem("Nồng độ bụi (ug/m³)", dustEntries, Color.MAGENTA)
                }
            }
        }
    }
}


@Composable
fun ChartItem(title: String, entries: List<Entry>, color: Int) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            factory = { context ->
                LineChart(context).apply {
                    description.isEnabled = false
                    axisRight.isEnabled = false
                    xAxis.position = XAxis.XAxisPosition.BOTTOM
                    xAxis.setDrawGridLines(false)
                    xAxis.granularity = 1f
                    xAxis.labelCount = 5
                    xAxis.valueFormatter = CustomXAxisValueFormatter(entries)

                    axisLeft.setDrawGridLines(true)
                    axisLeft.textColor = Color.DKGRAY
                    axisLeft.textSize = 12f
                    axisLeft.granularity = 1f

                    legend.isEnabled = true
                    legend.textColor = Color.BLACK
                }
            },
            update = { chart ->
                val dataSet = LineDataSet(entries, title).apply {
                    this.color = color
                    valueTextColor = Color.BLACK
                    lineWidth = 2.5f
                    circleRadius = 5f
                    setCircleColor(color)
                    setDrawValues(true)
                    valueTextSize = 10f

                    // Chỉ hiển thị giá trị nếu khác đáng kể so với điểm trước đó
                    valueFormatter = object : ValueFormatter() {
                        override fun getPointLabel(entry: Entry?): String {
                            val index = entries.indexOf(entry)
                            if (entry == null || index == -1) return ""

                            if (index > 0) {
                                val prevY = entries[index - 1].y
                                if (abs(entry.y - prevY) < 1f) {
                                    return ""
                                }
                            }
                            return entry.y.toInt().toString()
                        }
                    }
                }

                chart.data = LineData(dataSet)
                chart.animateX(500)
                chart.invalidate()
            }
        )
    }
}


// Custom value formatter for the X-axis
class CustomXAxisValueFormatter(private val entries: List<Entry>) : ValueFormatter() {
    override fun getAxisLabel(value: Float, axis: com.github.mikephil.charting.components.AxisBase?): String {
        return if (value.toInt() < entries.size) {
            value.toInt().toString()
        } else {
            ""
        }
    }
}