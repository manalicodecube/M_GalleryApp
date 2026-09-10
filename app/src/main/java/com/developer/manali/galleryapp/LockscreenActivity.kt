package com.developer.manali.galleryapp

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.developer.manali.galleryapp.data.AppPreferences

class LockscreenActivity : BaseActivity() {

    private lateinit var appPrefs: AppPreferences

    private var currentPin = ""
    private var confirmPin = ""

    private enum class LockMode {
        SETUP_NEW, CONFIRM_NEW, ENTER_PIN, RECOVERY
    }

    private var currentMode = LockMode.SETUP_NEW

    private lateinit var tvLockTitle: TextView
    private lateinit var tvLockSubtitle: TextView
    private lateinit var tvForgotPassword: TextView

    private lateinit var ivPin1: ImageView
    private lateinit var ivPin2: ImageView
    private lateinit var ivPin3: ImageView
    private lateinit var ivPin4: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lockscreen)



        appPrefs = AppPreferences.getInstance(this)

        tvLockTitle = findViewById(R.id.tvLockTitle)
        tvLockSubtitle = findViewById(R.id.tvLockSubtitle)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)

        ivPin1 = findViewById(R.id.ivPin1)
        ivPin2 = findViewById(R.id.ivPin2)
        ivPin3 = findViewById(R.id.ivPin3)
        ivPin4 = findViewById(R.id.ivPin4)

        if (appPrefs.appLockPin.isEmpty()) {
            currentMode = LockMode.SETUP_NEW
        } else {
            currentMode = LockMode.ENTER_PIN
        }

        updateUIForMode()

        setupDialer()

        findViewById<View>(R.id.btnBack)?.setOnClickListener {
            finish()
        }

        tvForgotPassword.setOnClickListener {
            if (appPrefs.securityAnswer.isNotEmpty()) {
                showRecoveryDialog()
            } else {
                Toast.makeText(this,
                    getString(R.string.no_security_question_set), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupDialer() {
        val dialIds = listOf(
            R.id.btnDial1, R.id.btnDial2, R.id.btnDial3,
            R.id.btnDial4, R.id.btnDial5, R.id.btnDial6,
            R.id.btnDial7, R.id.btnDial8, R.id.btnDial9,
            R.id.btnDial0
        )

        for ((index, id) in dialIds.withIndex()) {
            findViewById<View>(id).setOnClickListener {
                val digit = if (index == 9) "0" else (index + 1).toString()
                onDigitPressed(digit)
            }
        }

        findViewById<View>(R.id.btnDialBackspace).setOnClickListener {
            if (currentPin.isNotEmpty()) {
                currentPin = currentPin.dropLast(1)
                updatePinDots()
            }
        }
    }

    private fun updateUIForMode() {
        when (currentMode) {
            LockMode.SETUP_NEW -> {
                tvLockTitle.text = getString(R.string.set_password)
                tvLockSubtitle.text = getString(R.string.enter_a_4_digit_pin)
                tvForgotPassword.visibility = View.VISIBLE
            }
            LockMode.CONFIRM_NEW -> {
                tvLockTitle.text = getString(R.string.confirm_password)
                tvLockSubtitle.text = getString(R.string.re_enter_your_4_digit_pin)
                tvForgotPassword.visibility = View.VISIBLE
            }
            LockMode.ENTER_PIN -> {
                tvLockTitle.text =  getString(R.string.enter_password)
                tvLockSubtitle.text = getString(R.string.please_enter_your_4_digit_pin)
                tvForgotPassword.visibility = View.VISIBLE
            }
            LockMode.RECOVERY -> {
            }
        }
        currentPin = ""
        updatePinDots()
    }

    private fun onDigitPressed(digit: String) {
        if (currentPin.length < 4) {
            currentPin += digit
            updatePinDots()

            if (currentPin.length == 4) {
                handlePinComplete()
            }
        }
    }

    private fun updatePinDots() {
        val filled = ContextCompat.getColor(this, R.color.lumina_primary)
        val outline = ContextCompat.getColor(this, R.color.lumina_text_title)

        val imageViews = listOf(ivPin1, ivPin2, ivPin3, ivPin4)
        
        for (i in 0 until 4) {
            if (i < currentPin.length) {
                imageViews[i].setImageResource(R.drawable.circle)
                imageViews[i].imageTintList = android.content.res.ColorStateList.valueOf(filled)
            } else {
                imageViews[i].setImageResource(R.drawable.circle_outline)
                imageViews[i].imageTintList = android.content.res.ColorStateList.valueOf(outline)
            }
        }
    }

    private fun handlePinComplete() {
        when (currentMode) {
            LockMode.SETUP_NEW -> {
                confirmPin = currentPin
                currentMode = LockMode.CONFIRM_NEW
                updateUIForMode()
            }
            LockMode.CONFIRM_NEW -> {
                if (currentPin == confirmPin) {
                    showSecurityQuestionDialogForSetup()
                } else {
                    Toast.makeText(this,
                        getString(R.string.pins_do_not_match_try_again), Toast.LENGTH_SHORT).show()
                    currentMode = LockMode.SETUP_NEW
                    updateUIForMode()
                }
            }
            LockMode.ENTER_PIN -> {
                if (currentPin == appPrefs.appLockPin) {
                    Toast.makeText(this, getString(R.string.access_granted), Toast.LENGTH_SHORT).show()
                    startActivity(android.content.Intent(this, LockMediaActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, getString(R.string.incorrect_pin), Toast.LENGTH_SHORT).show()
                    currentPin = ""
                    updatePinDots()
                }
            }
            LockMode.RECOVERY -> { }
        }
    }

    private fun showSecurityQuestionDialogForSetup() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogView = layoutInflater.inflate(R.layout.dialog_security_question, null)
        dialog.setContentView(dialogView)

        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = (resources.displayMetrics.widthPixels * 0.88).toInt()
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvSecurityTitle)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvSecuritySubtitle)
        val spinnerQuestion = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerQuestion)
        val tvQuestion = dialogView.findViewById<TextView>(R.id.tvQuestion)
        val etAnswer = dialogView.findViewById<EditText>(R.id.etSecurityAnswer)
        val btnCancel = dialogView.findViewById<TextView>(R.id.btnCancelSecurity)
        val btnConfirm = dialogView.findViewById<TextView>(R.id.btnConfirmSecurity)

        tvTitle.text = getString(R.string.set_security_question)
        tvSubtitle.text =
            getString(R.string.this_will_help_you_recover_your_password_if_you_forget_it)
        
        spinnerQuestion.visibility = View.VISIBLE
        tvQuestion.visibility = View.GONE
        
        val questions = arrayOf(
            getString(R.string.what_is_your_favorite_pet_s_name),
            getString(R.string.what_city_were_you_born_in),
            getString(R.string.what_is_your_mother_s_maiden_name),
            getString(R.string.what_was_the_name_of_your_first_school),
            getString(R.string.what_is_your_favorite_book)
        )
        val adapter = android.widget.ArrayAdapter(this, R.layout.item_spinner, questions)
        adapter.setDropDownViewResource(R.layout.item_spinner)
        spinnerQuestion.adapter = adapter

        btnCancel.setOnClickListener {
            dialog.dismiss()
            currentMode = LockMode.SETUP_NEW
            updateUIForMode()
        }

        btnConfirm.setOnClickListener {
            val answer = etAnswer.text.toString().trim()
            if (answer.isNotEmpty()) {
                appPrefs.securityQuestion = spinnerQuestion.selectedItem.toString()
                appPrefs.securityAnswer = answer
                appPrefs.appLockPin = confirmPin
                Toast.makeText(this,
                    getString(R.string.password_set_successfully), Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                setResult(RESULT_OK)
                finish()
            } else {
                Toast.makeText(this, getString(R.string.please_enter_an_answer), Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun showRecoveryDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogView = layoutInflater.inflate(R.layout.dialog_security_question, null)
        dialog.setContentView(dialogView)

        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = (resources.displayMetrics.widthPixels * 0.88).toInt()
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvSecurityTitle)
        val tvSubtitle = dialogView.findViewById<TextView>(R.id.tvSecuritySubtitle)
        val spinnerQuestion = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerQuestion)
        val tvQuestion = dialogView.findViewById<TextView>(R.id.tvQuestion)
        val etAnswer = dialogView.findViewById<EditText>(R.id.etSecurityAnswer)
        val btnCancel = dialogView.findViewById<TextView>(R.id.btnCancelSecurity)
        val btnConfirm = dialogView.findViewById<TextView>(R.id.btnConfirmSecurity)

        tvTitle.text = getString(R.string.forgot_password)
        tvSubtitle.text = getString(R.string.answer_your_security_question_to_reset_the_pin)
        
        spinnerQuestion.visibility = View.GONE
        tvQuestion.visibility = View.VISIBLE
        tvQuestion.text = appPrefs.securityQuestion

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            val answer = etAnswer.text.toString().trim()
            if (answer.equals(appPrefs.securityAnswer, ignoreCase = true)) {
                Toast.makeText(this,
                    getString(R.string.answer_correct_please_set_a_new_pin), Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                appPrefs.appLockPin = "" // Reset
                currentMode = LockMode.SETUP_NEW
                updateUIForMode()
            } else {
                Toast.makeText(this, getString(R.string.incorrect_answer), Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }
}
