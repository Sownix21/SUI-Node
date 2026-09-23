package com.sonix21.suinode.core

import com.sonix21.suinode.APP

/** Lightweight runtime localization for the Compose-first UI. */
object UiLocale {
    private val fa = mapOf(
        "Home" to "خانه", "Clients" to "کاربران", "Inbounds" to "ورودی‌ها", "Outbounds" to "خروجی‌ها",
        "Tools" to "ابزارها", "Endpoints" to "نقاط پایانی", "Services" to "سرویس‌ها", "Routing" to "مسیریابی",
        "DNS" to "دی‌ان‌اس", "Core" to "هسته", "Core settings" to "تنظیمات هسته", "Panel settings" to "تنظیمات پنل",
        "App settings" to "تنظیمات برنامه", "Logs" to "گزارش‌ها", "Administrators" to "مدیران",
        "Backup & restore" to "پشتیبان‌گیری و بازیابی", "TLS templates" to "الگوهای TLS",
        "New Client" to "کاربر جدید", "Edit Client" to "ویرایش کاربر", "New Inbound" to "ورودی جدید",
        "Edit Inbound" to "ویرایش ورودی", "New Outbound" to "خروجی جدید", "Edit Outbound" to "ویرایش خروجی",
        "New Endpoint" to "نقطه پایانی جدید", "Edit Endpoint" to "ویرایش نقطه پایانی",
        "New Service" to "سرویس جدید", "Edit Service" to "ویرایش سرویس", "New TLS Template" to "الگوی TLS جدید",
        "Edit TLS Template" to "ویرایش الگوی TLS", "Type" to "نوع", "Tag" to "برچسب", "Name" to "نام",
        "Server" to "سرور", "Port" to "پورت", "Path" to "مسیر", "Address" to "نشانی", "Listen address" to "نشانی شنود",
        "Username" to "نام کاربری", "Password" to "گذرواژه", "Save" to "ذخیره", "Delete" to "حذف",
        "Cancel" to "انصراف", "Done" to "انجام شد", "Refresh" to "تازه‌سازی", "Import" to "درون‌ریزی",
        "Enable" to "فعال", "Enabled" to "فعال", "Disable" to "غیرفعال", "Advanced JSON" to "JSON پیشرفته",
        "Theme" to "پوسته", "Language" to "زبان", "Appearance" to "ظاهر",
        "Behavior" to "رفتار", "Refresh interval" to "فاصله تازه‌سازی", "App lock" to "قفل برنامه",
        "Settings" to "تنظیمات", "Basics" to "پایه", "Config" to "پیکربندی", "Links" to "پیوندها",
        "Server side" to "سمت سرور", "Client side" to "سمت کاربر", "Network" to "شبکه", "Transport" to "انتقال",
        "Multiplex" to "تسهیم", "Headers" to "سرآیندها", "Certificate" to "گواهی", "Private key" to "کلید خصوصی",
        "Public key" to "کلید عمومی", "Interface name" to "نام رابط", "Final server" to "سرور نهایی",
        "Strategy" to "راهبرد", "Rules" to "قوانین", "Rule sets" to "مجموعه قوانین", "Action" to "عملیات",
        "Logging" to "ثبت گزارش", "Output" to "خروجی", "Level" to "سطح", "Count" to "تعداد",
        "Domain" to "دامنه", "Domains" to "دامنه‌ها", "Email" to "ایمیل", "Timeout" to "مهلت",
        "Upload" to "آپلود", "Download" to "دانلود", "Traffic" to "ترافیک", "Range" to "بازه",
        "No endpoints" to "نقطه پایانی وجود ندارد", "No inbounds" to "ورودی وجود ندارد", "No clients" to "کاربری وجود ندارد",
        "No outbounds" to "خروجی وجود ندارد", "No services" to "سرویسی وجود ندارد", "No TLS templates" to "الگوی TLS وجود ندارد",
        "Validate & apply" to "اعتبارسنجی و اعمال", "Locked" to "قفل‌شده", "Panel configuration" to "پیکربندی پنل",
    )

    fun text(value: String): String {
        val translated = if (runCatching { APP.prefs.lang }.getOrDefault(Lang.EN) == Lang.FA) fa[value] ?: value else value
        return translated
    }

    fun digits(value: String): String = value
}
