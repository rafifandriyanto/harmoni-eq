package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.EqualizerViewModel
import com.example.ui.HarmoniEqApp
import com.example.ui.theme.HarmoniEqTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HarmoniEqTheme {
                val eqViewModel: EqualizerViewModel = viewModel()
                HarmoniEqApp(viewModel = eqViewModel)
            }
        }
    }
}
