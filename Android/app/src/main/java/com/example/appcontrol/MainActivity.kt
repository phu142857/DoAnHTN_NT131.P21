package com.example.appcontrol

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appcontrol.ui.theme.AppControlTheme
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class MainActivity : ComponentActivity() {
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = FirebaseDatabase.getInstance().getReference("air_quality")

        setContent {
            AppControlTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(database) {
                        startActivity(Intent(this, ChartActivity::class.java))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(database: DatabaseReference, onChartClick: () -> Unit) {
    var ppm by remember { mutableStateOf("-") }
    var temperature by remember { mutableStateOf("-") }
    var humidity by remember { mutableStateOf("-") }
    var gp2y1010 by remember { mutableStateOf("-") }
    var isLoading by remember { mutableStateOf(true) }

    fun fetchData() {
        isLoading = true
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                ppm = formatValue(snapshot.child("ppm").getValue(Float::class.java))
                temperature = formatValue(snapshot.child("temperature").getValue(Float::class.java))
                humidity = formatValue(snapshot.child("humidity").getValue(Float::class.java))
                gp2y1010 = formatValue(snapshot.child("dust_density").getValue(Float::class.java))
                isLoading = false
            }

            @SuppressLint("DefaultLocale")
            fun formatValue(value: Float?): String {
                return value?.let { String.format("%.2f", it) } ?: "-"
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Firebase", "Lỗi đọc dữ liệu: ${error.message}")
                isLoading = false
            }
        })
    }


    LaunchedEffect(Unit) { fetchData() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Giám sát chất lượng không khí") },
                colors = TopAppBarDefaults.mediumTopAppBarColors()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            if (isLoading) {
                // Hiển thị loading đẹp
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Đang tải dữ liệu...", fontSize = 16.sp, color = Color.Gray)
            } else {
                // Danh sách cảm biến
                SensorCard("Nồng độ PPM", ppm, "ppm", Color(0xFFEF5350))
                SensorCard("Nhiệt độ", temperature, "°C", Color(0xFF42A5F5))
                SensorCard("Độ ẩm", humidity, "%", Color(0xFF66BB6A))
                SensorCard("GP2Y1010 (Dust)", gp2y1010, "ug/m³", Color(0xFFFFA726))

                Spacer(modifier = Modifier.height(24.dp))

                Row {
                    Button(
                        onClick = { fetchData() },
                        enabled = !isLoading,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Text("Làm mới")
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Button(
                        onClick = onChartClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    ) {
                        Text("Xem biểu đồ")
                    }
                }
            }
        }
    }
}

@Composable
fun SensorCard(title: String, value: String, unit: String, color: Color) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "$value $unit",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black
            )
        }
    }
}
