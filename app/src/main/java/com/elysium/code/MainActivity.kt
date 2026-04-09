package com.elysium.code

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elysium.code.ui.ElysiumNavHost
import com.elysium.code.ui.screens.BootScreen
import com.elysium.code.ui.theme.ElysiumTheme
import com.elysium.code.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ElysiumTheme {
                val viewModel: MainViewModel = viewModel()
                val bootPhase by viewModel.bootPhase.collectAsState()
                val bootMessage by viewModel.bootMessage.collectAsState()
                val bootProgress by viewModel.bootProgress.collectAsState()
                val linuxProgress by viewModel.linuxSetupProgress.collectAsState()
                
                val currentProgress = if (bootPhase == MainViewModel.BootPhase.BOOTSTRAPPING_LINUX) {
                    linuxProgress
                } else {
                    bootProgress
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ElysiumTheme.colors.background
                ) {
                    AnimatedContent(
                        targetState = bootPhase == MainViewModel.BootPhase.READY,
                        label = "bootTransition",
                        transitionSpec = {
                            fadeIn(animationSpec = tween(800)) togetherWith
                            fadeOut(animationSpec = tween(400))
                        }
                    ) { isReady ->
                        if (isReady) {
                            ElysiumNavHost(viewModel)
                        } else {
                            BootScreen(
                                phase = bootPhase.name,
                                message = bootMessage,
                                progress = currentProgress
                            )
                        }
                    }
                }
            }
        }
    }
}
