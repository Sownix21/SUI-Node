package com.sonix21.suinode.ui.screens.settings

import android.content.Intent
import androidx.core.net.toUri

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sonix21.suinode.APP
import com.sonix21.suinode.BuildConfig
import com.sonix21.suinode.R
import com.sonix21.suinode.core.Lang
import com.sonix21.suinode.ui.glass.*
import kotlinx.coroutines.launch

/** Local information only: opening About never makes a network request. */
@Composable
fun AboutSection() {
    var open by remember { mutableStateOf(false) }
    val g = LocalGlass.current
    val fa = APP.prefs.lang == Lang.FA
    GlassCard(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.Info, contentDescription = null, tint = g.teal)
            Column(Modifier.weight(1f)) {
                Text(if (fa) "درباره برنامه" else "About", color = g.text, fontWeight = FontWeight.SemiBold)
                Text(if (fa) "هدف، ارتباط و امکانات" else "Purpose, connection & features", color = g.textFaint, fontSize = 12.sp)
            }
        }
    }
    if (open) AboutSheet(onDismiss = { open = false })
}

@Composable
private fun AboutSheet(onDismiss: () -> Unit) {
    val g = LocalGlass.current
    val fa = APP.prefs.lang == Lang.FA
    var showLicense by remember { mutableStateOf(false) }
    val context = LocalContext.current
    if (showLicense) {
        val license = remember(context) { context.resources.openRawResource(R.raw.gpl_3).bufferedReader().use { it.readText() } }
        AlertDialog(
            onDismissRequest = { showLicense = false },
            title = { Text("GNU GPLv3") },
            text = { Text(license, modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), fontSize = 12.sp) },
            confirmButton = { TextButton(onClick = { showLicense = false }) { Text(if (fa) "بستن" else "Close") } },
        )
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close() { scope.launchSheetClose(sheetState, onDismiss) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = g.surface,
        contentColor = g.text,
        scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null,
                    modifier = Modifier.size(64.dp).background(androidx.compose.ui.graphics.Color(0xFFE6EFE7), RoundedCornerShape(20.dp)))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("S-UI Node", fontWeight = FontWeight.Medium, fontSize = 18.sp)
                    Text("${if (fa) "نسخه" else "Version"} ${BuildConfig.VERSION_NAME}", color = g.textFaint, fontSize = 12.sp)
                }
                IconButton(onClick = { close() }) {
                    Icon(Icons.Rounded.Close, contentDescription = if (fa) "بستن" else "Close About", tint = g.textDim)
                }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(if (fa) "همراه شبکه شما." else "Built for your network.", color = g.text, fontSize = 28.sp, lineHeight = 33.sp, fontWeight = FontWeight.Medium)
                AboutBlock(if (fa) "پنل‌های شما، در دسترس شما" else "Your panels, within reach",
                    if (fa) "مدیریت یک یا چند پنل s-ui از گوشی اندرویدی، در محیطی شیشه‌ای و ساده. این برنامه ابزار مدیریت است و اتصال VPN دستگاه ایجاد نمی‌کند."
                    else "Manage one or multiple s-ui panels from your Android phone in a focused glass interface. This is a management app, not a VPN connection app.")
                AboutBlock(if (fa) "ارتباط مستقیم با APIv2" else "Direct communication through APIv2",
                    if (fa) "برنامه مستقیماً به نشانی پنل انتخاب‌شده متصل می‌شود و توکن را در هدر Token ارسال می‌کند. داده‌ها با GET دریافت و تغییرات با POST ارسال می‌شوند. ورود وب و API قدیمی استفاده نمی‌شوند. برای ارتباط امن، HTTPS و بررسی گواهی را فعال نگه دارید."
                    else "The app connects directly to your selected panel URL using APIv2 and the Token authentication header. GET requests retrieve data; POST requests submit changes. No web login or legacy API is used. Use HTTPS with certificate verification for secure communication.")
                AboutBlock(if (fa) "امکانات مدیریتی" else "Management features",
                    if (fa) "پروفایل‌های چندپنلی؛ مدیریت کاربران، اینباندها، اوتباندها و قالب‌های TLS؛ تنظیمات DNS و مسیریابی؛ آمار، گزارش‌ها و پشتیبان‌گیری. گزینه‌های وابسته با انتخاب پروتکل یا فعال‌کردن تنظیم مربوط نمایش داده می‌شوند. امکانات قابل استفاده به نسخه پنل و قابلیت‌های APIv2 آن بستگی دارد."
                    else "Multiple panel profiles; client, inbound, outbound and TLS-template management; DNS and routing configuration; statistics, logs, and database backup tools. Dependent options appear when their protocol or setting is selected. Available operations depend on your panel version and its APIv2 support.")
                AboutBlock(if (fa) "کنترل و حریم خصوصی" else "Control & privacy",
                    if (fa) "اطلاعات اتصال در مخزن رمزگذاری‌شده با کلید Android Keystore نگهداری می‌شوند. پیش از ویرایش، رکورد کامل دریافت می‌شود و خطاهای همگام‌سازی نمایش داده می‌شوند. فایل‌های پشتیبان پایگاه داده حساس‌اند و باید امن نگهداری شوند."
                    else "Panel credentials are stored in an encrypted vault protected by Android Keystore. Full records are retrieved before editing, and synchronization errors remain visible. Exported database backups contain sensitive information: store them securely.")
                Text(if (fa) "همراه مستقل برای پروژه s-ui ساخته‌شده توسط alireza0." else "An independent management app for the s-ui project by alireza0.", color = g.textFaint, fontSize = 12.sp)
                AboutBlock(if (fa) "مجوز و حقوق نشر" else "License & copyright",
                    if (fa) "Copyright © 2026 SONIX. این برنامه تحت مجوز GPL-3.0-only منتشر شده است؛ تغییر و بازنشر طبق شرایط آن مجاز است و هیچ ضمانتی ارائه نمی‌شود."
                    else "Copyright © 2026 SONIX. Licensed under GPL-3.0-only. You may modify and redistribute this software under its terms. Provided without warranty.")
                GhostButton(if (fa) "مشاهده متن کامل مجوز" else "Read full license") { showLicense = true }
            }
            HorizontalDivider(color = g.strokeLo)
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("Developed by SONIX", color = g.teal, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                IconButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/Sownix21".toUri())) }
                        .onFailure { ToastBus.show(if (fa) "مرورگری برای باز کردن پیوند در دسترس نیست" else "No browser available to open the profile") }
                }) {
                    Icon(painterResource(R.drawable.ic_github),
                        contentDescription = if (fa) "پروفایل SONIX در GitHub" else "Open SONIX GitHub profile", tint = g.textDim)
                }
            }
        }
    }
}

private fun kotlinx.coroutines.CoroutineScope.launchSheetClose(state: SheetState, onDismiss: () -> Unit) =
    launch { state.hide(); onDismiss() }

@Composable
private fun AboutBlock(title: String, body: String) {
    val g = LocalGlass.current
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, color = g.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        Text(body, color = g.textDim, fontSize = 13.sp, lineHeight = 21.sp)
    }
}
