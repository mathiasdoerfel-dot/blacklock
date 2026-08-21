package com.example.blacklock
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnTestLock).setOnClickListener {
            val ok = LockAccessibilityService.lockScreen()
            if (!ok) Toast.makeText(this, "Barrierefreiheitsdienst noch nicht aktiv!", Toast.LENGTH_LONG).show()
        }
    }
}
