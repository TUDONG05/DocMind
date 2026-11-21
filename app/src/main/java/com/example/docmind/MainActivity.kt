package com.example.docmind

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import android.view.View
import com.example.docmind.settings.SettingsActivity
import com.example.docmind.settings.SettingsAwareActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : SettingsAwareActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        findViewById<View>(R.id.openSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}