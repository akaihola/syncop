package fi.kaihola.syncop

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import fi.kaihola.syncop.ui.SyncopApp
import fi.kaihola.syncop.ui.theme.SyncopTheme

class MainActivity : ComponentActivity() {
    private val vm: SyncopViewModel by viewModels()

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.hasPermission = granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm.hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!vm.hasPermission) permission.launch(Manifest.permission.RECORD_AUDIO)
        setContent {
            SyncopTheme {
                SyncopApp(
                    vm = vm,
                    onRequestPermission = { permission.launch(Manifest.permission.RECORD_AUDIO) },
                    onExport = ::shareWav,
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        vm.stop()
    }

    private fun shareWav() {
        val file = vm.exportWav()
        val uri = FileProvider.getUriForFile(this, "fi.kaihola.syncop.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Export recording"))
    }
}
