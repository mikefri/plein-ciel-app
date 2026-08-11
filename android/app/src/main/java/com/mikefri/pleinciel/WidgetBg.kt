package com.mikefri.pleinciel

fun widgetBg(code: Int, isDay: Boolean): Int = when {
    code <= 1 && isDay  -> 0xFF1470C8.toInt()   // bleu ciel
    code <= 1           -> 0xFF0B1E3F.toInt()   // bleu nuit
    code <= 3           -> 0xFF3B6FA0.toInt()   // bleu gris
    code <= 48          -> 0xFF6B7280.toInt()   // gris brouillard
    code <= 67          -> 0xFF475569.toInt()   // ardoise (pluie)
    code <= 77          -> 0xFF64748B.toInt()   // gris bleuté (neige)
    code <= 82          -> 0xFF475569.toInt()   // ardoise (averses)
    else                -> 0xFF2D1B4E.toInt()   // violet (orage)
}
