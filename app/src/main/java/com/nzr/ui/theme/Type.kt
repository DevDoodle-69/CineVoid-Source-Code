package com.nzr.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.Font
import com.nzr.R

val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val NetflixFont = FontFamily(
    Font(googleFont = GoogleFont("Poppins"), fontProvider = provider)
)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = NetflixFont,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = NetflixFont,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        letterSpacing = (-0.3).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = NetflixFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = NetflixFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        letterSpacing = (-0.1).sp,
    ),
    labelLarge = TextStyle(
        fontFamily = NetflixFont,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = NetflixFont,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = NetflixFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = NetflixFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = NetflixFont,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        letterSpacing = 0.2.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = NetflixFont,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 1.2.sp,
    )
)
