package com.Muncho.kaptal

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.fragment.app.FragmentActivity

val LocalActivity = staticCompositionLocalOf<FragmentActivity> {
    error("No Activity found")
}
