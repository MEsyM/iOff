package com.dualactionwindows.dawdrive.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dualactionwindows.dawdrive.AccountManager

@Composable
fun FirstRunOnboarding(
    cs: Boolean,
    state: AccountManager.AccountState,
    onSignUp: (String, String, String) -> Unit,
    onLogin: (String, String) -> Unit,
    onGuest: () -> Unit
) {
    var mode by remember { mutableStateOf("welcome") }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = LoneRiderColors.Background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF071523), LoneRiderColors.Background)
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                "LONE RIDER",
                color = LoneRiderColors.Cyan,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )
            Text(
                if (cs) "Tvůj profil. V každém autě." else "Your profile. In every car.",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 38.sp
            )
            Text(
                if (cs) {
                    "Přihlas se a synchronizuj XP, statistiky a hráčský profil. Nebo pokračuj jako host — kdykoliv se můžeš přihlásit později."
                } else {
                    "Sign in to sync XP, stats and your rider profile. Or continue as a guest and connect later."
                },
                color = LoneRiderColors.TextSecondary,
                fontSize = 15.sp,
                lineHeight = 21.sp
            )

            if (mode == "welcome") {
                OnboardingFeature("☁", if (cs) "Cloud progress" else "Cloud progress", if (cs) "XP a statistiky napříč zařízeními" else "XP and stats across devices")
                OnboardingFeature("👤", if (cs) "Rodinné profily" else "Family profiles", if (cs) "Připraveno pro více hráčů" else "Ready for multiple players")
                OnboardingFeature("🚙", "Lone Rider", if (cs) "Stejný účet pro telefon i auto" else "One account for phone and car")

                PrimaryWide(if (cs) "VYTVOŘIT ÚČET" else "CREATE ACCOUNT") { mode = "signup" }
                SecondaryWide(if (cs) "PŘIHLÁSIT SE" else "SIGN IN") { mode = "login" }
                SecondaryWide(if (cs) "POKRAČOVAT JAKO HOST" else "CONTINUE AS GUEST", onGuest)
            } else {
                Text(
                    if (mode == "signup") {
                        if (cs) "Vytvořit účet" else "Create account"
                    } else {
                        if (cs) "Přihlášení" else "Sign in"
                    },
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                if (mode == "signup") {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (cs) "Jméno profilu" else "Profile name") },
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("E-mail") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (cs) "Heslo" else "Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                if (!state.message.isNullOrBlank()) {
                    Text(state.message, color = LoneRiderColors.Amber, fontSize = 13.sp)
                }
                PrimaryWide(
                    if (state.busy) {
                        if (cs) "PRACUJI…" else "WORKING…"
                    } else if (mode == "signup") {
                        if (cs) "VYTVOŘIT A POKRAČOVAT" else "CREATE & CONTINUE"
                    } else {
                        if (cs) "PŘIHLÁSIT" else "SIGN IN"
                    },
                    enabled = !state.busy
                ) {
                    if (mode == "signup") onSignUp(email, password, name) else onLogin(email, password)
                }
                SecondaryWide(if (cs) "ZPĚT" else "BACK") { mode = "welcome" }
            }
        }
    }
}

@Composable
private fun OnboardingFeature(icon: String, title: String, subtitle: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = LoneRiderColors.Surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, LoneRiderColors.Border)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(icon, fontSize = 24.sp)
            Column {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(subtitle, color = LoneRiderColors.TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun AccountCloudScreen(
    cs: Boolean,
    state: AccountManager.AccountState,
    onBack: () -> Unit,
    onSignUp: (String, String, String) -> Unit,
    onLogin: (String, String) -> Unit,
    onLogout: () -> Unit,
    onSync: () -> Unit
) {
    var signup by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(state.email.orEmpty()) }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(containerColor = LoneRiderColors.SurfaceRaised)
            ) { Text(if (cs) "‹ ZPĚT" else "‹ BACK") }
            Text("☁  " + (if (cs) "ÚČET & CLOUD" else "ACCOUNT & CLOUD"), color = Color.White, fontWeight = FontWeight.Bold)
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = LoneRiderColors.Surface,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, LoneRiderColors.Cyan.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (state.loggedIn) (state.profileName ?: "Rider") else if (cs) "Host" else "Guest",
                    color = Color.White,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    if (state.loggedIn) state.email.orEmpty() else if (cs) "Progress je uložen pouze v tomto telefonu." else "Progress is stored only on this phone.",
                    color = LoneRiderColors.TextSecondary,
                    fontSize = 13.sp
                )
                if (state.loggedIn) {
                    val sync = if (state.lastSyncAt > 0L) {
                        if (cs) "Cloud sync aktivní" else "Cloud sync active"
                    } else {
                        if (cs) "Připraveno k první synchronizaci" else "Ready for first sync"
                    }
                    Text("● $sync", color = LoneRiderColors.Cyan, fontSize = 12.sp)
                }
            }
        }

        if (!state.loggedIn) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = LoneRiderColors.Surface,
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { signup = false },
                            colors = ButtonDefaults.buttonColors(containerColor = if (!signup) LoneRiderColors.Blue else LoneRiderColors.SurfaceRaised)
                        ) { Text(if (cs) "PŘIHLÁSIT" else "SIGN IN") }
                        Button(
                            onClick = { signup = true },
                            colors = ButtonDefaults.buttonColors(containerColor = if (signup) LoneRiderColors.Blue else LoneRiderColors.SurfaceRaised)
                        ) { Text(if (cs) "NOVÝ ÚČET" else "NEW ACCOUNT") }
                    }
                    if (signup) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(if (cs) "Jméno profilu" else "Profile name") },
                            singleLine = true
                        )
                    }
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("E-mail") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (cs) "Heslo" else "Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                    PrimaryWide(
                        if (state.busy) {
                            if (cs) "PRACUJI…" else "WORKING…"
                        } else if (signup) {
                            if (cs) "VYTVOŘIT ÚČET" else "CREATE ACCOUNT"
                        } else {
                            if (cs) "PŘIHLÁSIT SE" else "SIGN IN"
                        },
                        enabled = !state.busy
                    ) {
                        if (signup) onSignUp(email, password, name) else onLogin(email, password)
                    }
                }
            }
        } else {
            PrimaryWide(
                if (state.busy) {
                    if (cs) "SYNCHRONIZUJI…" else "SYNCING…"
                } else {
                    if (cs) "SYNCHRONIZOVAT TEĎ" else "SYNC NOW"
                },
                enabled = !state.busy,
                onClick = onSync
            )
            SecondaryWide(
                if (cs) "ODHLÁSIT" else "SIGN OUT",
                enabled = !state.busy,
                onClick = onLogout
            )
        }

        if (!state.message.isNullOrBlank()) {
            Text(state.message, color = LoneRiderColors.Amber, fontSize = 13.sp)
        }

        Text(
            if (cs) {
                "Cloud ukládá účet, hráčský profil a agregovaný herní progress. Hra dál funguje offline."
            } else {
                "Cloud stores your account, player profile and aggregated game progress. Games continue to work offline."
            },
            color = LoneRiderColors.TextSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun PrimaryWide(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = LoneRiderColors.Cyan, contentColor = Color(0xFF071018))
    ) {
        Text(text, modifier = Modifier.padding(vertical = 5.dp), fontWeight = FontWeight.Black)
    }
}

@Composable
private fun SecondaryWide(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = LoneRiderColors.SurfaceRaised, contentColor = Color.White)
    ) {
        Text(text, modifier = Modifier.padding(vertical = 5.dp), fontWeight = FontWeight.Bold)
    }
}
