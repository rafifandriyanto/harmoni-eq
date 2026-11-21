package com.example

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.ui.EqualizerViewModel
import com.example.ui.HarmoniEqApp
import com.example.ui.theme.HarmoniEqTheme

class MainActivity : ComponentActivity() {
    private val eqViewModel: EqualizerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Hold splash screen strictly until DataStore settings & last AutoEQ state are restored
        // (zero artificial delay).
        splashScreen.setKeepOnScreenCondition {
            !eqViewModel.uiState.value.libraryRestored
        }

        // Smooth fade + subtle icon scale exit animation into the matching #0E1013 console canvas.
        splashScreen.setOnExitAnimationListener { splashViewProvider ->
            val splashView = splashViewProvider.view
            if (!ValueAnimator.areAnimatorsEnabled()) {
                splashViewProvider.remove()
                return@setOnExitAnimationListener
            }
            val iconView = runCatching { splashViewProvider.iconView }.getOrNull()
            runCatching {
                val fadeOut = ObjectAnimator.ofFloat(splashView, View.ALPHA, 1f, 0f).apply {
                    duration = 220L
                    interpolator = DecelerateInterpolator()
                }
                if (iconView != null) {
                    val scaleX = ObjectAnimator.ofFloat(iconView, View.SCALE_X, 1f, 1.08f)
                    val scaleY = ObjectAnimator.ofFloat(iconView, View.SCALE_Y, 1f, 1.08f)
                    val iconFade = ObjectAnimator.ofFloat(iconView, View.ALPHA, 1f, 0f)
                    AnimatorSet().apply {
                        duration = 220L
                        interpolator = DecelerateInterpolator()
                        playTogether(fadeOut, scaleX, scaleY, iconFade)
                        doOnEnd { splashViewProvider.remove() }
                        start()
                    }
                } else {
                    fadeOut.doOnEnd { splashViewProvider.remove() }
                    fadeOut.start()
                }
            }.onFailure {
                splashViewProvider.remove()
            }
        }

        enableEdgeToEdge()
        setContent {
            HarmoniEqTheme {
                HarmoniEqApp(viewModel = eqViewModel)
            }
        }
    }
}
