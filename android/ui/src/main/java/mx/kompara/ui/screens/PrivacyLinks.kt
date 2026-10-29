package mx.kompara.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import mx.kompara.ui.BuildConfig
import java.net.URI

/** Release configuration must be verified by operations; empty values never become fixture links. */
object PrivacyLinks {
    fun supportUrl(number: String): String? = number.takeIf { it.matches(Regex("[1-9][0-9]{7,14}")) }?.let { "https://wa.me/$it" }
    fun deletionUrl(value: String): String? = runCatching {
        URI(value).takeIf { it.scheme == "https" && it.host == "kompara.mx" && it.path == "/borrar" && it.userInfo == null }?.toASCIIString()
    }.getOrNull()
    val support: String? get() = supportUrl(BuildConfig.SUPPORT_WHATSAPP)
    val deletion: String? get() = deletionUrl(BuildConfig.ACCOUNT_DELETION_URL)
    fun supportDraft(report: String, number: String = BuildConfig.SUPPORT_WHATSAPP): String? =
        supportUrl(number)?.let { Uri.parse(it).buildUpon().appendQueryParameter("text", report).build().toString() }
    fun open(context: Context, url: String): Boolean = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrDefault(false)
}
