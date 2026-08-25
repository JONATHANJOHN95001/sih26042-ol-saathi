package `in`.gov.tribalfln

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import `in`.gov.tribalfln.data.StudentProgressDatabase
import `in`.gov.tribalfln.mesh.ClassroomMeshSync
import `in`.gov.tribalfln.ui.BilingualMaterialStudioFragment
import `in`.gov.tribalfln.ui.CurriculumBrowserFragment
import `in`.gov.tribalfln.ui.HomeDashboardFragment
import `in`.gov.tribalfln.ui.ClassroomDialogueFragment
import `in`.gov.tribalfln.ui.ZeroNetworkMeshSyncFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import `in`.gov.tribalfln.ui.SihTelemetryOverlayService
import `in`.gov.tribalfln.util.TelemetryMonitor

class NipunEducatorDashboardActivity : AppCompatActivity(),
    HomeDashboardFragment.DashboardNavigationListener {

    companion object {
        private const val TAG = "TeacherDashboard"
        private const val FRAGMENT_TAG_HOME = "frag_home"
        private const val FRAGMENT_TAG_CLASSROOM = "frag_classroom"
        private const val FRAGMENT_TAG_LESSONS = "frag_lessons"
        private const val FRAGMENT_TAG_TOOLS = "frag_tools"
        private const val FRAGMENT_TAG_SYNC = "frag_sync"
    }

    private var scope: CoroutineScope? = null
    private var meshJob: Job? = null
    private val isGrading = AtomicBoolean(false)
    private var activeFragmentTag: String = FRAGMENT_TAG_HOME

    private var logoTapCount = 0
    private var lastLogoTapTime = 0L

    private val perms = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { p ->
        if (p.values.all { it }) {
            Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Denied: ${p.filter { !it.value }.keys}", Toast.LENGTH_LONG).show()
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            SihTelemetryOverlayService.start(this)
            Toast.makeText(this, "🚀 SIH Live Telemetry HUD Activated", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Overlay permission is required for SIH Telemetry HUD", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nipun_educator_dashboard)
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        setupToolbar()
        setupBottomNavigation()
        requestPermissions()
        initMesh()
        if (savedInstanceState == null) {
            navigateToFragment(FRAGMENT_TAG_HOME, HomeDashboardFragment.newInstance())
        } else {
            val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
            val selectedId = when (savedInstanceState.getString("active_tab")) {
                FRAGMENT_TAG_CLASSROOM -> R.id.nav_classroom
                FRAGMENT_TAG_LESSONS -> R.id.nav_lessons
                FRAGMENT_TAG_TOOLS -> R.id.nav_tools
                FRAGMENT_TAG_SYNC -> R.id.nav_sync
                else -> R.id.nav_home
            }
            bottomNav.selectedItemId = selectedId
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("active_tab", activeFragmentTag)
    }

    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        supportActionBar?.subtitle = "NIPUN FLN — Santhali (Ol Chiki)"

        // Secret 5-tap trigger on Ministry / NIPUN logo & toolbar
        toolbar.setNavigationOnClickListener { handleSecretTelemetryTap() }
        toolbar.setOnClickListener { handleSecretTelemetryTap() }
    }

    private fun handleSecretTelemetryTap() {
        val now = System.currentTimeMillis()
        if (now - lastLogoTapTime > 2500L) {
            logoTapCount = 1
        } else {
            logoTapCount++
        }
        lastLogoTapTime = now

        if (logoTapCount >= 5) {
            logoTapCount = 0
            toggleTelemetryOverlay()
        } else if (logoTapCount >= 3) {
            val remaining = 5 - logoTapCount
            Toast.makeText(this, "$remaining more taps for SIH Telemetry HUD", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleTelemetryOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
            Toast.makeText(this, "Grant overlay permission for SIH Telemetry HUD", Toast.LENGTH_LONG).show()
        } else {
            if (TelemetryMonitor.telemetryState.value.isOverlayActive) {
                SihTelemetryOverlayService.stop(this)
                Toast.makeText(this, "SIH Telemetry HUD Disabled", Toast.LENGTH_SHORT).show()
            } else {
                SihTelemetryOverlayService.start(this)
                Toast.makeText(this, "🚀 SIH Live Telemetry HUD Activated", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.lang_san -> { setSelectedLanguage("san", "Santhali (Ol Chiki)"); true }
            R.id.lang_hoc -> { setSelectedLanguage("hoc", "Ho (Warang Citi)"); true }
            R.id.lang_mfq -> { setSelectedLanguage("mfq", "Mundari (Bani)"); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setSelectedLanguage(languageCode: String, displayName: String) {
        supportActionBar?.subtitle = "NIPUN FLN — $displayName"
        Log.d(TAG, "Target language set to: $displayName ($languageCode)")
        supportFragmentManager.fragments.forEach { fragment ->
            when (fragment) {
                is ClassroomDialogueFragment -> fragment.onLanguageChanged(languageCode)
                is CurriculumBrowserFragment -> fragment.onLanguageChanged(languageCode)
                is BilingualMaterialStudioFragment -> fragment.onLanguageChanged(languageCode)
            }
        }
        Toast.makeText(this, "Language: $displayName", Toast.LENGTH_SHORT).show()
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.setOnItemSelectedListener { item ->
            val (tag, fragment) = when (item.itemId) {
                R.id.nav_home -> FRAGMENT_TAG_HOME to HomeDashboardFragment.newInstance()
                R.id.nav_classroom -> FRAGMENT_TAG_CLASSROOM to ClassroomDialogueFragment.newInstance()
                R.id.nav_lessons -> FRAGMENT_TAG_LESSONS to BilingualMaterialStudioFragment.newInstance()
                R.id.nav_tools -> FRAGMENT_TAG_TOOLS to CurriculumBrowserFragment.newInstance()
                R.id.nav_sync -> FRAGMENT_TAG_SYNC to ZeroNetworkMeshSyncFragment.newInstance()
                else -> FRAGMENT_TAG_HOME to HomeDashboardFragment.newInstance()
            }
            navigateToFragment(tag, fragment)
            true
        }
    }

    private fun navigateToFragment(tag: String, newFragment: Fragment) {
        if (tag == activeFragmentTag) return
        val fm = supportFragmentManager
        fm.beginTransaction().apply {
            val currentFrag = fm.findFragmentByTag(activeFragmentTag)
            if (currentFrag != null) hide(currentFrag)
            val existing = fm.findFragmentByTag(tag)
            if (existing != null) show(existing) else add(R.id.fragment_container, newFragment, tag)
            setReorderingAllowed(true)
            commitNowAllowingStateLoss()
        }
        activeFragmentTag = tag
    }

    override fun navigateToScreen(screenId: Int) {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.selectedItemId = screenId
    }

    private fun requestPermissions() {
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) Toast.makeText(this, "All granted", Toast.LENGTH_SHORT).show()
        else permLauncher.launch(missing.toTypedArray())
    }

    private fun initMesh() {
        try {
            ClassroomMeshSync.initialize(applicationContext)
            meshJob = scope?.launch {
                ClassroomMeshSync.connectionState.collect { state -> Log.d(TAG, "Mesh state: $state") }
            }
        } catch (e: Exception) { Log.e(TAG, "Mesh init failed", e) }
    }

    private fun refreshStudentData() {
        scope?.launch {
            try {
                val dao = TribalFLNApplication.studentProgressDatabase?.progressDao()
                val count = dao?.getActiveStudentCount()?.first() ?: 0
                val mastery = dao?.getClassMasteryPercentage()?.first() ?: 0f
                Log.d(TAG, "Students: $count, Mastery: ${String.format("%.1f%%", mastery)}")
            } catch (_: Exception) {}
        }
    }

    override fun onResume() { super.onResume(); refreshStudentData() }
    override fun onDestroy() { meshJob?.cancel(); scope?.cancel(); ClassroomMeshSync.release(); super.onDestroy() }
}
