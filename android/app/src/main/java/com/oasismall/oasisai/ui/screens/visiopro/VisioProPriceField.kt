package com.oasismall.oasisai.ui.screens.visiopro

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.defaultMinSize

/** Keeps only calculator-friendly price characters. */
fun filterVisioProPriceInput(raw: String): String =
    raw.filter { it.isDigit() || it == ',' || it == '.' || it == ' ' }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VisioProNumericPriceField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "DA",
    enabled: Boolean = true,
    minWidthDp: Int = 88,
    onCommit: () -> Unit = {},
    onFocused: () -> Unit = {},
) {
    val bringIntoView = remember { BringIntoViewRequester() }
    var scrollIntoView by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(filterVisioProPriceInput(it)) },
        label = { Text(label) },
        modifier = modifier
            .defaultMinSize(minWidth = minWidthDp.dp)
            .bringIntoViewRequester(bringIntoView)
            .onFocusEvent { event ->
                if (event.isFocused) {
                    scrollIntoView = true
                    onFocused()
                }
            },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onCommit() }),
    )

    LaunchedEffect(scrollIntoView) {
        if (scrollIntoView) {
            bringIntoView.bringIntoView()
            scrollIntoView = false
        }
    }
}
