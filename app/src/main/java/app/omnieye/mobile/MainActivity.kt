package app.omnieye.mobile

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = TextView(this).apply {
            text = "OmniEye Mobile\nX4 WiFi + cellular backend MVP"
            textSize = 20f
            setPadding(32, 48, 32, 32)
        }
        setContentView(status)
    }
}
