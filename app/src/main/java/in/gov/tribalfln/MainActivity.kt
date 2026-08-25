package `in`.gov.tribalfln

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent



class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, NipunEducatorDashboardActivity::class.java))
        finish()
    }
}

