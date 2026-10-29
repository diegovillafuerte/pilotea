package mx.kompara.ui.format

/** Grouping is presentation only: cursor offsets still edit the original decimal digits. */
internal object DecimalGrouping : androidx.compose.ui.text.input.VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        val raw = text.text
        if (!raw.matches(Regex("[0-9]+(\\.[0-9]*)?"))) return androidx.compose.ui.text.input.TransformedText(text, androidx.compose.ui.text.input.OffsetMapping.Identity)
        val whole = raw.substringBefore('.')
        val formatted = whole.reversed().chunked(3).joinToString(",").reversed() + if (raw.contains('.')) ".${raw.substringAfter('.')}" else ""
        val positions = formatted.indices.filter { formatted[it] != ',' }
        return androidx.compose.ui.text.input.TransformedText(androidx.compose.ui.text.AnnotatedString(formatted), object : androidx.compose.ui.text.input.OffsetMapping {
            override fun originalToTransformed(offset: Int) = positions.getOrElse(offset) { formatted.length }
            override fun transformedToOriginal(offset: Int) = formatted.take(offset).count { it != ',' }
        })
    }
}
