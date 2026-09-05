package com.cyprienbrisset.myportal.ui.settings

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyprienbrisset.myportal.data.settings.SettingsRepository
import com.cyprienbrisset.myportal.integration.IcsCalendarRepository
import com.cyprienbrisset.myportal.ui.sumi.HankoSeal
import com.cyprienbrisset.myportal.ui.sumi.SumiPrimaryButton
import com.cyprienbrisset.myportal.ui.theme.Kinari
import com.cyprienbrisset.myportal.ui.theme.Mincho
import com.cyprienbrisset.myportal.ui.theme.Shu
import com.cyprienbrisset.myportal.ui.theme.SumiLine
import com.cyprienbrisset.myportal.ui.theme.SumiMuted
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class IcsSettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository(app)

    val savedUrl = repo.googleIcsUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _verifyResult = mutableStateOf<String?>(null)
    val verifyResult: String? get() = _verifyResult.value

    private val _verifying = mutableStateOf(false)
    val verifying: Boolean get() = _verifying.value

    fun save(url: String) {
        viewModelScope.launch { repo.setGoogleIcsUrl(url.trim()) }
    }

    fun clear() {
        viewModelScope.launch { repo.setGoogleIcsUrl(null) }
        _verifyResult.value = null
    }

    fun verify(url: String) {
        if (url.isBlank()) return
        _verifying.value = true
        _verifyResult.value = null
        viewModelScope.launch {
            val events = IcsCalendarRepository.upcoming(url.trim(), System.currentTimeMillis())
            _verifyResult.value = if (events == null) "Erreur : URL inaccessible ou format invalide."
            else "${events.size} événement(s) trouvé(s) dans les 7 prochains jours."
            _verifying.value = false
        }
    }
}

@Composable
fun IcsSettingsScreen(onBack: () -> Unit, vm: IcsSettingsViewModel = viewModel()) {
    val savedUrl by vm.savedUrl.collectAsStateWithLifecycle()
    var draft by rememberSaveable(savedUrl) { mutableStateOf(savedUrl ?: "") }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 32.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            HankoSeal("朱", size = 40.dp, onClick = onBack)
            Spacer(Modifier.width(14.dp))
            Text("Agenda Google", fontFamily = Mincho, color = Kinari, fontSize = 22.sp)
        }
        Text(
            "Colle l'URL ICS privée de ton agenda Google ci-dessous. Tu la trouves dans Agenda Google → Paramètres → [ton agenda] → URL secrète au format iCal.",
            color = SumiMuted, fontSize = 14.sp,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("URL ICS", color = SumiMuted) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.save(draft) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Shu,
                unfocusedBorderColor = SumiLine,
                focusedTextColor = Kinari,
                unfocusedTextColor = Kinari,
            ),
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SumiPrimaryButton("Enregistrer", onClick = { vm.save(draft) })
            SumiPrimaryButton("Vérifier", onClick = { vm.verify(draft) })
            if (savedUrl != null) {
                SumiPrimaryButton("Effacer", onClick = { draft = ""; vm.clear() })
            }
        }
        vm.verifyResult?.let { result ->
            Spacer(Modifier.height(16.dp))
            Text(
                result,
                color = if (result.startsWith("Erreur")) Shu else Kinari,
                fontSize = 14.sp,
            )
        }
        if (vm.verifying) {
            Spacer(Modifier.height(12.dp))
            Text("Vérification…", color = SumiMuted, fontSize = 14.sp)
        }
    }
}
