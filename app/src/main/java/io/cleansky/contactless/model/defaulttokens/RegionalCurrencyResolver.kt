package io.cleansky.contactless.model.defaulttokens

import java.util.Locale

internal object RegionalCurrencyResolver {
    private val euCountries =
        setOf(
            "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR",
            "DE", "GR", "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL",
            "PL", "PT", "RO", "SK", "SI", "ES", "SE",
            "IS", "LI", "NO",
            "AD", "MC", "SM", "VA", "ME", "XK",
        )

    private val chfCountries = setOf("CH", "LI")
    private val gbpCountries = setOf("GB", "IM", "JE", "GG", "GI", "FK", "SH")
    private val jpyCountries = setOf("JP")

    private fun currentCountry(): String = normalizeCountryCode(Locale.getDefault().country)

    private fun normalizeCountryCode(countryCode: String): String = countryCode.uppercase()

    fun isEURegion(countryCode: String = currentCountry()): Boolean = normalizeCountryCode(countryCode) in euCountries

    fun isCHFRegion(countryCode: String = currentCountry()): Boolean = normalizeCountryCode(countryCode) in chfCountries

    fun isGBPRegion(countryCode: String = currentCountry()): Boolean = normalizeCountryCode(countryCode) in gbpCountries

    fun isJPYRegion(countryCode: String = currentCountry()): Boolean = normalizeCountryCode(countryCode) in jpyCountries

    fun preferredCurrencyCode(countryCode: String = currentCountry()): String {
        return when {
            isJPYRegion(countryCode) -> "JPY"
            isCHFRegion(countryCode) -> "CHF"
            isGBPRegion(countryCode) -> "GBP"
            isEURegion(countryCode) -> "EUR"
            else -> "USD"
        }
    }
}
