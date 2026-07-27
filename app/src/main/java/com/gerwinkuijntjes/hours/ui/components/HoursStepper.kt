package com.gerwinkuijntjes.hours.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gerwinkuijntjes.hours.R
import com.gerwinkuijntjes.hours.ui.currentLocale
import com.gerwinkuijntjes.hours.ui.formatHours
import com.gerwinkuijntjes.hours.ui.parseNumber
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Hours entry: type any value, or nudge by a quarter.
 *
 * The buttons snap to the next whole quarter rather than adding 0.25 blindly, so
 * a typed 3.4 becomes 3.5 on "+" instead of the less useful 3.65.
 */
@Composable
fun HoursStepper(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = currentLocale()
    val current = parseNumber(text) ?: 0.0

    fun emit(value: Double) =
        onTextChange(formatHours(value.coerceAtLeast(0.0), locale, maxDecimals = 2, minDecimals = 2))

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilledTonalIconButton(
            onClick = { emit((ceil(current * 4) - 1) / 4) },
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                Icons.Default.Remove,
                contentDescription = stringResource(R.string.quarter_less),
                modifier = Modifier.size(17.dp)
            )
        }

        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            ),
            suffix = {
                Text(
                    text = stringResource(R.string.hours_unit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(),
            modifier = Modifier.width(96.dp)
        )

        FilledTonalIconButton(
            onClick = { emit((floor(current * 4) + 1) / 4) },
            modifier = Modifier.size(34.dp)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.quarter_more),
                modifier = Modifier.size(17.dp)
            )
        }
    }
}
