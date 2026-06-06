package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.data.database.TaskDatabase
import com.example.ui.screen.DashboardScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.DashboardViewModel
import com.example.viewmodel.DashboardViewModelFactory

class MainActivity : ComponentActivity() {
  
  private val database by lazy { TaskDatabase.getDatabase(this) }
  private val taskDao by lazy { database.taskDao() }
  
  private val viewModel: DashboardViewModel by viewModels {
    DashboardViewModelFactory(taskDao)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
          DashboardScreen(
            viewModel = viewModel,
            modifier = Modifier.padding(innerPadding)
          )
        }
      }
    }
  }
}

@androidx.compose.runtime.Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
  androidx.compose.material3.Text(text = "Hello $name!", modifier = modifier)
}
