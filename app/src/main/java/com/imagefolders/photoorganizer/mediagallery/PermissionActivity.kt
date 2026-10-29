package com.imagefolders.photoorganizer.mediagallery

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.imagefolders.photoorganizer.mediagallery.databinding.ActivityPermissionBinding

class PermissionActivity : BaseActivity() {

    private lateinit var binding: ActivityPermissionBinding

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Proceed to main screen even if user chose "Don't allow" or skipped some permissions
        navigateToMain()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Util.hasAllPermissions(this)) {
            navigateToMain()
            return
        }

        enableEdgeToEdge()

        binding = ActivityPermissionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootPermission) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )
            insets
        }

        setupViews()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        if (Util.hasAllPermissions(this)) {
            navigateToMain()
        } else {
            setupViews()
        }
    }

    private fun setupViews() {
        val hasStorage = Util.hasStoragePermissions(this)
        val hasCamera = Util.hasCameraPermission(this)

        if (hasStorage && !hasCamera) {
            // Only Camera missing
            binding.tvPermissionTitle.setText(R.string.camera_access)
            binding.tvPermissionDesc.setText(R.string.camera_permission_dialog_desc)
            binding.ivGalleryIcon.setImageResource(R.drawable.camera)
            binding.cardFeatureOrganization.visibility = View.GONE
            binding.cardFeatureSecurity.visibility = View.GONE
        } else if (!hasStorage && hasCamera) {
            // Only Storage missing
            binding.tvPermissionTitle.setText(R.string.lumina_permission_title)
            binding.tvPermissionDesc.setText(R.string.lumina_permission_description)
            binding.ivGalleryIcon.setImageResource(R.drawable.ic_permission_gallery)
            binding.cardFeatureOrganization.visibility = View.VISIBLE
            binding.cardFeatureSecurity.visibility = View.VISIBLE
        } else {
            // Both missing
            binding.tvPermissionTitle.setText(R.string.lumina_permission_title)
            binding.tvPermissionDesc.setText(R.string.lumina_permission_description)
            binding.ivGalleryIcon.setImageResource(R.drawable.ic_permission_gallery)
            binding.cardFeatureOrganization.visibility = View.VISIBLE
            binding.cardFeatureSecurity.visibility = View.VISIBLE
        }
    }

    private fun setupListeners() {
        binding.btnAllowAccess.setOnClickListener {
            requestPermissions()
        }

        binding.btnNotNow.setOnClickListener {
            navigateToMain()
        }

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateToMain()
            }
        })
    }

    private fun requestPermissions() {
        val missing = Util.getMissingPermissions(this)
        if (missing.isEmpty()) {
            navigateToMain()
            return
        }

        val prefs = PreferencesUtility.getInstance(this)
        val hasRequestedBefore = prefs.isPermissionRequestedBefore()
        val allPermanentlyDenied = hasRequestedBefore && missing.all { permission ->
            !ActivityCompat.shouldShowRequestPermissionRationale(this, permission)
        }

        if (allPermanentlyDenied) {
            showSettingsDialog()
        } else {
            prefs.setPermissionRequestedBefore(true)
            permissionLauncher.launch(missing)
        }
    }

    private fun showSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_required)
            .setMessage(
                "Please enable the required permissions in App Settings to access all features of the gallery."
            )
            .setPositiveButton(R.string.open_settings) { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton(R.string.skip) { _, _ ->
                navigateToMain()
            }
            .show()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}