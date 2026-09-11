
package com.developer.manali.galleryapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.developer.manali.galleryapp.databinding.ActivityPermissionBinding

class PermissionActivity : BaseActivity() {

    private lateinit var binding: ActivityPermissionBinding

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->

        var isAllGranted = true

        for (isGranted in permissions.values) {
            if (!isGranted) {
                isAllGranted = false
                break
            }
        }

        if (isAllGranted) {
            navigateToMain()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {

                val partialGranted = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                ) == PackageManager.PERMISSION_GRANTED

                if (partialGranted) {
                    navigateToMain()
                    return@registerForActivityResult
                }
            }

            if (!shouldShowPermissionRationale()) {
                showSettingsDialog()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.permission_is_required_to_access_your_media),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        binding = ActivityPermissionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootPermission) { v, insets ->

            val systemBars =
                insets.getInsets(WindowInsetsCompat.Type.systemBars())

            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            )

            insets
        }

        setupListeners()
    }

    private fun setupListeners() {

        binding.btnAllowAccess.setOnClickListener {
            requestPermissions()
        }

        binding.btnNotNow.setOnClickListener {
            navigateToMain()
        }
    }

    private fun requestPermissions() {

        val permissionsToRequest = getRequiredPermissions()

        permissionLauncher.launch(permissionsToRequest)
    }

    private fun getRequiredPermissions(): Array<String> {

        val permissions = mutableListOf<String>()

        permissions.add(Manifest.permission.CAMERA)

        when {

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {

                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
                permissions.add(
                    Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                )
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {

                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            }

            else -> {

                permissions.add(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            }
        }

        return permissions.toTypedArray()
    }

    private fun shouldShowPermissionRationale(): Boolean {

        val permissions = getRequiredPermissions()

        for (permission in permissions) {

            if (shouldShowRequestPermissionRationale(permission)) {
                return true
            }
        }

        return false
    }

    private fun showSettingsDialog() {

        AlertDialog.Builder(this)
            .setTitle("Permission Needed")
            .setMessage(
                "Gallery requires camera and storage permissions to display, manage and capture your photos. Please enable them in App Settings."
            )
            .setPositiveButton("Settings") { _, _ ->

                val intent =
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts(
                            "package",
                            packageName,
                            null
                        )
                    }

                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun navigateToMain() {

        val intent = Intent(
            this,
            MainActivity::class.java
        )

        intent.flags =
            Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK

        startActivity(intent)

        finish()
    }
}