package com.sonix21.suinode.core

import com.sonix21.suinode.APP

/** Lightweight runtime localization for the Compose-first UI. */
object UiLocale {
    private val fa = mapOf(
        "Source IP conditions" to "شرط‌های آی‌پی مبدأ",
        "Source IP match" to "نوع تطبیق آی‌پی مبدأ",
        "Source IP CIDRs" to "بازه‌های آی‌پی مبدأ (CIDR)",
        "Private source IPs" to "آی‌پی‌های خصوصی مبدأ",
        "Global reset: leave empty or use off to disable. Example: 0 0 1 * * resets monthly in the panel time zone. s-ui 1.6.4 validates the schedule and applies changes without restarting; the first reset occurs at the next scheduled boundary. Resets re-enable all clients." to "بازنشانی سراسری: برای غیرفعال‌کردن، خالی بگذارید یا off بنویسید. نمونهٔ 0 0 1 * * ترافیک را ماهانه، مطابق منطقهٔ زمانی پنل بازنشانی می‌کند. پنل ۱.۶.۴ زمان‌بندی را اعتبارسنجی و بدون راه‌اندازی مجدد اعمال می‌کند؛ نخستین بازنشانی در موعد بعدی انجام می‌شود. بازنشانی همهٔ کاربران را دوباره فعال می‌کند.",
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
        "Next" to "بعدی", "Apply" to "اعمال", "Days" to "روز", "Hours" to "ساعت",
        "Choose date & time" to "انتخاب تاریخ و ساعت", "Clear expiry · unlimited" to "حذف تاریخ انقضا · نامحدود",
        "App lock & PIN" to "قفل برنامه و رمز عددی", "App PIN" to "رمز عددی برنامه",
        "Current app PIN" to "رمز فعلی برنامه", "New app PIN" to "رمز جدید برنامه",
        "Confirm new app PIN" to "تکرار رمز جدید برنامه", "Save app PIN" to "ذخیره رمز برنامه",
        "Set up app PIN" to "تنظیم رمز برنامه", "Change app PIN" to "تغییر رمز برنامه",
        "Remove app PIN" to "حذف رمز برنامه", "Close PIN settings" to "بستن تنظیمات رمز",
        "Biometric unlock" to "باز کردن قفل با اثر انگشت یا تشخیص چهره",
        "Unlock with app PIN" to "باز کردن قفل با رمز برنامه", "Unlock with biometrics" to "باز کردن قفل با احراز هویت زیستی",
        "Confirm with app PIN" to "تأیید با رمز برنامه", "Save PIN and unlock" to "ذخیره رمز و باز کردن قفل",
        "Monitoring & alerts" to "پایش و هشدارها", "Check interval" to "فاصله بررسی‌ها",
        "Expiry alerts" to "هشدار انقضا", "Expiry warning unit" to "واحد زمان هشدار انقضا",
        "Warn when remaining time is at most" to "هشدار وقتی زمان باقی‌مانده حداکثر برابر است با",
        "Repeat unresolved alerts" to "تکرار هشدارهای برطرف‌نشده", "Every hour" to "هر ساعت",
        "Start calculating usage" to "شروع محاسبه مصرف", "At the next billing reset" to "از بازنشانی دوره بعد",
        "Now / continue current tracking" to "اکنون / ادامه پایش فعلی", "Reset hour" to "ساعت بازنشانی",
        "Reset minute" to "دقیقه بازنشانی", "Monthly reset day" to "روز بازنشانی ماهانه",
        "Billing timezone" to "منطقه زمانی دوره مصرف",
        "Next renewal" to "تاریخ تمدید بعدی", "Choose date" to "انتخاب تاریخ",
        "Gregorian calendar" to "تقویم میلادی", "Billing reset time" to "ساعت بازنشانی دوره مصرف",
        "Quiet from" to "شروع ساعات سکوت", "Quiet until" to "پایان ساعات سکوت",
        "Hour" to "ساعت",
    )

    fun text(value: String): String {
        val translated = if (runCatching { APP.prefs.lang }.getOrDefault(Lang.EN) == Lang.FA) fa[value] ?: value else value
        return translated
    }

    fun digits(value: String): String = value
}
