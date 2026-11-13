package com.example.ytsaver

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTPS address of YOUR mail service (see backend/vault-mail). An app cannot send e-mail by itself without putting mail-account
 * passwords inside the APK, which anyone could extract, so the code is posted to this address and the service e-mails it.
 * Leave it empty and "Reset PIN" explains that the mail service is not set up (the temporary PIN is then never created or shown).
 */
const val RECOVERY_MAIL_ENDPOINT = ""

/** Posts {to, code, lang} to the mail service. The service builds the e-mail text itself, so the app cannot be used to send other content. */
object RecoveryMailer {
    fun configured() = RECOVERY_MAIL_ENDPOINT.startsWith("https://")

    /** Blocking. True when the service accepted the request. */
    fun send(to: String, code: String, ar: Boolean): Boolean {
        if (!configured()) return false
        val c = URL(RECOVERY_MAIL_ENDPOINT).openConnection() as HttpURLConnection
        return try {
            c.requestMethod = "POST"
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            c.outputStream.use { it.write(JSONObject().put("to", to).put("code", code).put("lang", if (ar) "ar" else "en").put("app", "VibeVault").toString().toByteArray(Charsets.UTF_8)) }
            c.responseCode in 200..299
        } catch (_: Exception) {
            false
        } finally {
            c.disconnect()
        }
    }
}

enum class ResetRequest { Sent, WrongEmail, NoEmailSet, MailNotConfigured, SendFailed, Cooldown }

object VaultRecovery {
    private const val K_FAILS = "vault_rec_fails"       // "count:firstAt"
    private const val K_SENT = "vault_rec_sent"          // comma-separated send times (last hour)

    private fun now() = System.currentTimeMillis()

    /** Checks the e-mail the user typed against the one saved, then asks the mail service to send a one-time PIN. */
    suspend fun requestCode(ctx: Context, typed: String, ar: Boolean): ResetRequest = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        val saved = VaultStore.recoveryEmail(app) ?: return@withContext ResetRequest.NoEmailSet
        // too many wrong addresses in 10 minutes: wait
        val f = Prefs.str(app, K_FAILS)?.split(":")
        val count = f?.getOrNull(0)?.toIntOrNull() ?: 0
        val first = f?.getOrNull(1)?.toLongOrNull() ?: 0L
        if (count >= 5 && now() - first < 10 * 60_000L) return@withContext ResetRequest.Cooldown
        if (!typed.trim().equals(saved, ignoreCase = true)) {
            val fresh = count == 0 || now() - first >= 10 * 60_000L
            Prefs.putStr(app, K_FAILS, "${if (fresh) 1 else count + 1}:${if (fresh) now() else first}")
            return@withContext ResetRequest.WrongEmail
        }
        Prefs.putStr(app, K_FAILS, null)
        // at most 3 codes per hour
        val sent = (Prefs.str(app, K_SENT) ?: "").split(",").mapNotNull { it.toLongOrNull() }.filter { now() - it < 3600_000L }
        if (sent.size >= 3) return@withContext ResetRequest.Cooldown
        if (!RecoveryMailer.configured()) return@withContext ResetRequest.MailNotConfigured     // nothing is created or shown
        val code = VaultStore.newTempPin(app)
        val ok = RecoveryMailer.send(saved, code, ar)
        if (!ok) { VaultStore.clearTempPin(app); return@withContext ResetRequest.SendFailed }
        Prefs.putStr(app, K_SENT, (sent + now()).joinToString(","))
        ResetRequest.Sent
    }
}

// =====================================================================================================
//  UI
// =====================================================================================================

/** "Forgot the PIN?": Erase the vault, and Reset PIN (by e-mail), side by side. */
@Composable
fun ForgotPinRow(onOpenVault: () -> Unit, onChanged: () -> Unit = {}) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var eraseOpen by remember { mutableStateOf(false) }
    var resetOpen by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(tr("Forgot the PIN?", "نسيت الرمز؟"), color = Muted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { eraseOpen = true }) { Text(tr("Erase the vault", "مسح الخزنة"), color = Red, fontSize = 13.sp) }
            TextButton(onClick = { resetOpen = true }) { Text(tr("Reset PIN", "إعادة تعيين الرمز"), fontSize = 13.sp) }
        }
    }
    if (eraseOpen) {
        AlertDialog(
            onDismissRequest = { eraseOpen = false },
            title = { Text(tr("Erase the vault?", "مسح الخزنة؟")) },
            text = { Text(tr("Every file in the vault is deleted for good and the PIN is removed. This can't be undone.", "تُحذف كل ملفات الخزنة نهائيًا ويُزال الرمز. لا يمكن التراجع.")) },
            confirmButton = {
                TextButton(onClick = {
                    eraseOpen = false
                    scope.launch { VaultStore.eraseAll(ctx); onChanged() }
                }) { Text(tr("Erase everything", "مسح كل شيء"), color = Red) }
            },
            dismissButton = { TextButton(onClick = { eraseOpen = false }) { Text(tr("Cancel", "إلغاء")) } },
        )
    }
    if (resetOpen) ResetPinDialog(onClose = { resetOpen = false }, onOpenVault = { resetOpen = false; onOpenVault(); onChanged() })
}

/** Reset the PIN: confirm the recovery e-mail (shown masked), get a one-time PIN by e-mail, enter it. */
@Composable
private fun ResetPinDialog(onClose: () -> Unit, onOpenVault: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ar = L.ar
    val masked = remember { VaultStore.maskedEmail(ctx) }
    var step by remember { mutableIntStateOf(if (masked == null) 0 else 1) }     // 0 = no e-mail set, 1 = confirm e-mail, 2 = enter code
    var typed by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(tr("Reset the vault PIN", "إعادة تعيين رمز الخزنة")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (step) {
                    0 -> Text(tr("No recovery e-mail was set for this vault, so the PIN can't be reset. The only way out is to erase the vault.", "لم يُعيَّن بريد استعادة لهذه الخزنة، لذا لا يمكن إعادة تعيين الرمز. الحل الوحيد هو مسح الخزنة."))
                    1 -> {
                        Text(tr("Confirm the recovery e-mail you set for the vault:", "أكّد بريد الاستعادة الذي عيّنته للخزنة:"))
                        Text(masked.orEmpty(), fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                        OutlinedTextField(
                            value = typed, onValueChange = { typed = it; msg = null }, singleLine = true,
                            label = { Text(tr("Full e-mail address", "عنوان البريد كاملًا")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(tr("A temporary PIN is sent to this address. It works once, for 15 minutes.", "يُرسل رمز مؤقت إلى هذا العنوان. يعمل مرة واحدة ولمدة 15 دقيقة."), color = Muted, fontSize = 12.sp)
                    }
                    else -> {
                        Text(tr("A temporary PIN was sent to $masked. Enter it here:", "تم إرسال رمز مؤقت إلى $masked. أدخله هنا:"))
                        PinField(code, { code = it; msg = null }, tr("Temporary PIN", "الرمز المؤقت"))
                        Text(tr("Next you'll choose a new PIN inside the vault.", "بعد ذلك ستختار رمزًا جديدًا داخل الخزنة."), color = Muted, fontSize = 12.sp)
                    }
                }
                if (busy) CircularProgressIndicator(Modifier.padding(top = 4.dp))
                msg?.let { Text(it, color = Red, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            when (step) {
                1 -> TextButton(enabled = !busy && typed.contains('@'), onClick = {
                    busy = true; msg = null
                    scope.launch {
                        when (VaultRecovery.requestCode(ctx, typed, ar)) {
                            ResetRequest.Sent -> step = 2
                            ResetRequest.WrongEmail -> msg = L.t("That is not the e-mail that was set.", "هذا ليس البريد الذي تم تعيينه.")
                            ResetRequest.NoEmailSet -> step = 0
                            ResetRequest.MailNotConfigured -> msg = L.t("E-mail sending isn't set up in this version of the app, so the PIN can't be reset by e-mail. You can still erase the vault.", "إرسال البريد غير مُعدّ في هذا الإصدار، لذا لا يمكن إعادة تعيين الرمز بالبريد. ما زال بإمكانك مسح الخزنة.")
                            ResetRequest.SendFailed -> msg = L.t("Couldn't send the e-mail. Check your connection and try again.", "تعذّر إرسال البريد. تحقق من الاتصال وحاول مجددًا.")
                            ResetRequest.Cooldown -> msg = L.t("Too many tries. Wait a while and try again.", "محاولات كثيرة. انتظر قليلًا ثم حاول مجددًا.")
                        }
                        busy = false
                    }
                }) { Text(tr("Send temporary PIN", "إرسال الرمز المؤقت")) }
                2 -> TextButton(enabled = !busy && code.length >= 6, onClick = {
                    when (VaultStore.checkTempPin(ctx, code)) {
                        0 -> onOpenVault()
                        1 -> { msg = L.t("Wrong code.", "الرمز غير صحيح."); code = "" }
                        3 -> { msg = L.t("Too many wrong codes. Ask for a new one.", "رموز خاطئة كثيرة. اطلب رمزًا جديدًا."); step = 1; code = "" }
                        else -> { msg = L.t("The code expired. Ask for a new one.", "انتهت صلاحية الرمز. اطلب رمزًا جديدًا."); step = 1; code = "" }
                    }
                }) { Text(tr("Continue", "متابعة")) }
                else -> {}
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onClose) { Text(tr("Cancel", "إلغاء")) } },
    )
}

/** The compulsory new PIN after a reset (cannot be dismissed; leaving the vault page keeps the old PIN and locks it). */
@Composable
fun NewPinRequiredDialog() {
    val ctx = LocalContext.current
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(tr("Choose a new PIN", "اختر رمزًا جديدًا")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Your PIN was reset. Choose a new PIN to open the vault. This step is required.", "تمت إعادة تعيين الرمز. اختر رمزًا جديدًا لفتح الخزنة. هذه الخطوة إلزامية."), color = Muted, fontSize = 13.sp)
                PinField(p1, { p1 = it; err = null }, tr("New PIN (4–12 digits)", "الرمز الجديد (4–12 رقمًا)"))
                PinField(p2, { p2 = it; err = null }, tr("Repeat the PIN", "أعد كتابة الرمز"))
                err?.let { Text(it, color = Red, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    p1.length !in 4..12 || !p1.all { it.isDigit() } -> err = L.t("The PIN needs 4 to 12 digits.", "يجب أن يتكون الرمز من 4 إلى 12 رقمًا.")
                    p1 != p2 -> err = L.t("The two PINs don't match.", "الرمزان غير متطابقين.")
                    else -> VaultStore.finishReset(ctx, p1)
                }
            }) { Text(tr("Save PIN", "حفظ الرمز")) }
        },
    )
}

/** Set or change the recovery e-mail. [requirePin] asks for the current PIN first (Settings); inside the open vault it is not needed. */
@Composable
fun RecoveryEmailDialog(requirePin: Boolean, onDone: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Recovery e-mail", "بريد الاستعادة")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    tr("If you forget the vault PIN, a temporary PIN can be sent to this address. Use an address only you can open.",
                        "إذا نسيت رمز الخزنة يمكن إرسال رمز مؤقت إلى هذا العنوان. استخدم عنوانًا لا يفتحه غيرك."),
                    color = Muted, fontSize = 13.sp,
                )
                if (requirePin) PinField(pin, { pin = it; err = null }, tr("Current PIN", "الرمز الحالي"))
                OutlinedTextField(
                    value = email, onValueChange = { email = it; err = null }, singleLine = true,
                    label = { Text(tr("E-mail address", "عنوان البريد")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = again, onValueChange = { again = it; err = null }, singleLine = true,
                    label = { Text(tr("Repeat the e-mail address", "أعد كتابة عنوان البريد")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
                )
                err?.let { Text(it, color = Red, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    requirePin && !VaultStore.verify(ctx, pin) -> err = L.t("The PIN is wrong.", "الرمز غير صحيح.")
                    !VaultStore.isValidEmail(email) -> err = L.t("That doesn't look like an e-mail address.", "هذا لا يبدو عنوان بريد صحيحًا.")
                    !email.trim().equals(again.trim(), ignoreCase = true) -> err = L.t("The two addresses don't match.", "العنوانان غير متطابقين.")
                    else -> { VaultStore.setRecoveryEmail(ctx, email); onDone() }
                }
            }) { Text(tr("Save", "حفظ")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (requirePin) tr("Cancel", "إلغاء") else tr("Later", "لاحقًا")) } },
    )
}
