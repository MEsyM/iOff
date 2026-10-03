package com.dualactionwindows.dawdrive

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

class AccountActivity : ComponentActivity() {

    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var profileName: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Lone Rider Account"

        val scroll = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 36, 36, 36)
            setBackgroundColor(Color.rgb(6, 16, 30))
        }
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(scroll)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (CloudAccountManager.state(this).signedIn) {
            CloudAccountManager.syncProgressAsync(this, force = true)
        }
    }

    private fun render(message: String? = null) {
        root.removeAllViews()
        val state = CloudAccountManager.state(this)

        text("LONE RIDER", 28, true)
        text("Cloud account & progress sync", 16, false)
        spacer()

        status = text(
            message ?: if (state.signedIn) {
                "Signed in as ${state.email ?: "account"}"
            } else {
                "Sign in or create an account. Your local progress stays on this phone until you sign in."
            },
            15,
            false
        )
        spacer()

        if (!state.signedIn) {
            email = input("Email", false)
            password = input("Password (min. 6 characters)", true)
            button("SIGN IN") {
                runNetwork("Signing in…") {
                    CloudAccountManager.login(this, email.text.toString(), password.text.toString()).getOrThrow()
                    "Signed in. Progress synchronized."
                }
            }
            button("CREATE ACCOUNT") {
                runNetwork("Creating account…") {
                    CloudAccountManager.signUp(this, email.text.toString(), password.text.toString()).getOrThrow()
                    "Account created. Existing local progress was synchronized."
                }
            }
            return
        }

        button("SYNC NOW") {
            runNetwork("Synchronizing…") {
                CloudAccountManager.syncProgress(this, force = true)
                "Cloud progress synchronized."
            }
        }

        text("Profiles", 20, true)
        loadProfiles()

        profileName = input("New profile name", false)
        button("ADD PROFILE") {
            val name = profileName.text.toString().trim()
            if (name.isBlank()) {
                status.text = "Enter a profile name."
            } else {
                runNetwork("Creating profile…") {
                    CloudAccountManager.createProfile(this, name).getOrThrow()
                    CloudAccountManager.syncProgress(this, force = true)
                    "Profile $name created and selected."
                }
            }
        }

        spacer()
        button("SIGN OUT") {
            runNetwork("Signing out…") {
                CloudAccountManager.logout(this)
                "Signed out. Local progress remains on this device."
            }
        }
    }

    private fun loadProfiles() {
        text("Loading profiles…", 14, false).also { placeholder ->
            Thread {
                val result = CloudAccountManager.profiles(this)
                runOnUiThread {
                    if (root.indexOfChild(placeholder) >= 0) root.removeView(placeholder)
                    result.onSuccess { profiles ->
                        val active = CloudAccountManager.state(this).activeProfileId
                        if (profiles.isEmpty()) {
                            text("No profiles yet.", 14, false)
                        } else {
                            profiles.forEach { profile ->
                                button((if (profile.id == active) "✓ " else "") + profile.name) {
                                    CloudAccountManager.setActiveProfile(this, profile.id)
                                    render("Profile ${profile.name} selected. Sync started.")
                                }
                            }
                        }
                    }.onFailure {
                        text("Could not load profiles: ${it.message}", 14, false)
                    }
                }
            }.start()
        }
    }

    private fun runNetwork(progress: String, block: () -> String) {
        status.text = progress
        Thread {
            val result = runCatching(block)
            runOnUiThread {
                result.onSuccess { render(it) }
                    .onFailure { status.text = it.message ?: "Operation failed" }
            }
        }.start()
    }

    private fun input(hint: String, secret: Boolean): EditText =
        EditText(this).also {
            it.hint = hint
            it.setHintTextColor(Color.rgb(130, 148, 166))
            it.setTextColor(Color.WHITE)
            it.setSingleLine(true)
            if (secret) {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            } else if (hint.contains("Email", ignoreCase = true)) {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            }
            root.addView(it, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

    private fun button(label: String, action: () -> Unit): Button =
        Button(this).also {
            it.text = label
            it.setOnClickListener { action() }
            root.addView(it, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

    private fun text(value: String, size: Int, bold: Boolean): TextView =
        TextView(this).also {
            it.text = value
            it.textSize = size.toFloat()
            it.setTextColor(Color.WHITE)
            if (bold) it.setTypeface(it.typeface, android.graphics.Typeface.BOLD)
            root.addView(it)
        }

    private fun spacer() {
        root.addView(TextView(this).apply { height = 24 })
    }
}
