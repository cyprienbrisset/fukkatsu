package com.cyprienbrisset.myportal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cyprienbrisset.myportal.ui.AppNav
import com.cyprienbrisset.myportal.ui.theme.MyPortalTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyPortalTheme { AppNav() }
        }
    }
}
