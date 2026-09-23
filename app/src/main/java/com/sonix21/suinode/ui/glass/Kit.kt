package com.sonix21.suinode.ui.glass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import com.sonix21.suinode.core.UiLocale

/**
 * JSONObject is intentionally used for lossless panel round-trips, but it is not
 * snapshot-aware. All editor controls bump this signal after mutations so parent
 * screens immediately re-evaluate conditional sections.
 */
object JsonEditSignal {
    private val state = mutableIntStateOf(0)
    val revision: Int get() = state.intValue
    fun bump() { state.intValue++ }
}

// ---------------------------------------------------------------- modifiers

/** A single rounded translucent layer, with no nested highlights or per-card shadows. */
fun Modifier.glassSurface(
    corner: Dp = 24.dp,
    alphaHi: Float = 0.10f,
    alphaLo: Float = 0.045f,
    elevation: Dp = 0.dp,
): Modifier = composed {
    val g = LocalGlass.current
    val shape = RoundedCornerShape(corner)
    this.clip(shape)
        .background(Brush.verticalGradient(listOf(
            g.glassHi.copy(alpha = if (g.isDark) alphaHi else 0.82f),
            g.glassLo.copy(alpha = if (g.isDark) alphaLo else 0.64f),
        )))
        .border(0.7.dp, Brush.verticalGradient(listOf(g.strokeHi, g.strokeLo)), shape)
}

/** Springy press feedback: gently sinks and dims while pressed. */
fun Modifier.pressScale(
    interaction: MutableInteractionSource,
    pressed: Float = 0.965f,
): Modifier = composed {
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressed else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "pressScale",
    )
    graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (isPressed) 0.92f else 1f }
}

fun Modifier.innerFill(corner: Dp = 14.dp): Modifier = composed {
    val g = LocalGlass.current
    this.clip(RoundedCornerShape(corner))
        .background(Brush.verticalGradient(listOf(g.innerFill, g.innerFill.copy(alpha = g.innerFill.alpha * 0.72f))))
        .border(0.7.dp, g.strokeLo, RoundedCornerShape(corner))
}

// ---------------------------------------------------------------- cards

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp = 24.dp,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .then(if (onClick != null) Modifier.pressScale(interaction) else Modifier)
            .then(
                if (onClick != null)
                    Modifier.clickable(interactionSource = interaction, indication = null) { onClick() }
                else Modifier
            )
            .glassSurface(corner)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(UiLocale.text(title), color = LocalGlass.current.textDim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)
        Spacer(Modifier.weight(1f))
        if (trailing != null) trailing()
    }
}

// ---------------------------------------------------------------- buttons

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    Button(
        onClick = { onClick(); JsonEditSignal.bump() }, enabled = enabled && !loading,
        modifier = modifier.heightIn(min = 52.dp), shape = RoundedCornerShape(18.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 13.dp),
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        else Text(UiLocale.text(text), color = if (enabled) MaterialTheme.colorScheme.onPrimary else LocalGlass.current.textFaint,
            fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, tint: Color? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val g = LocalGlass.current
    FilledTonalButton(
        onClick = { onClick(); JsonEditSignal.bump() }, enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = g.innerFill, contentColor = tint ?: g.textDim),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) { Text(UiLocale.text(text), fontWeight = FontWeight.Medium, fontSize = 13.sp) }
}

@Composable
fun IconGhostButton(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color? = null, contentDesc: String? = null) {
    val g = LocalGlass.current
    IconButton(onClick = { onClick(); JsonEditSignal.bump() }, modifier = modifier.size(48.dp)) {
        Icon(icon, contentDescription = contentDesc, tint = tint ?: g.textDim, modifier = Modifier.size(22.dp))
    }
}

// ---------------------------------------------------------------- chrome

@Composable
fun GlassTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val g = LocalGlass.current
    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 14.dp)) {
        if (onBack != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconGhostButton(Icons.AutoMirrored.Filled.ArrowBack, onBack, contentDesc = "Back")
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) { actions?.invoke(this) }
            }
            Spacer(Modifier.height(8.dp))
            Text(UiLocale.text(title), color = g.text, fontSize = 26.sp, letterSpacing = (-0.8).sp,
                fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(UiLocale.text(title), color = g.text, fontSize = 30.sp, letterSpacing = (-0.8).sp,
                    fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) { actions?.invoke(this) }
            }
        }
        if (!subtitle.isNullOrBlank()) Text(UiLocale.digits(subtitle), color = g.textFaint, fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun StatusDot(color: Color, size: Dp = 8.dp, pulse: Boolean = false) {
    Box(Modifier.size(size).background(color, CircleShape))
}

// ---------------------------------------------------------------- text input

@Composable
fun GlassTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    obscure: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = 12,
    enabled: Boolean = true,
    supporting: String? = null,
    labelAbove: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailing: (@Composable () -> Unit)? = null,
) {
    val g = LocalGlass.current
    var visible by remember(obscure) { mutableStateOf(!obscure) }
    Column(modifier) {
        if (labelAbove) Text(UiLocale.text(label), color = g.textFaint, fontSize = 12.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        OutlinedTextField(
            value = value, onValueChange = { onValueChange(it); JsonEditSignal.bump() },
            modifier = Modifier.fillMaxWidth(),
            label = if (labelAbove) null else ({ Text(UiLocale.text(label), fontSize = 12.sp) }),
            placeholder = hint?.let { { Text(it, fontSize = 14.sp) } },
            supportingText = supporting?.let { { Text(UiLocale.text(it), fontSize = 11.sp) } },
            singleLine = singleLine, minLines = if (singleLine) 1 else minLines,
            maxLines = if (singleLine) 1 else maxLines, enabled = enabled,
            textStyle = TextStyle(color = g.text, fontSize = 14.sp),
            keyboardOptions = if (obscure) KeyboardOptions(keyboardType = if (keyboardOptions.keyboardType == KeyboardType.NumberPassword) KeyboardType.NumberPassword else KeyboardType.Password, autoCorrectEnabled = false) else keyboardOptions,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = g.teal, unfocusedBorderColor = g.strokeHi,
                focusedContainerColor = g.innerFill, unfocusedContainerColor = g.innerFill,
                cursorColor = g.teal, focusedLabelColor = g.teal, unfocusedLabelColor = g.textFaint,
                focusedTextColor = g.text, unfocusedTextColor = g.text,
            ),
            trailingIcon = if (obscure) ({
                IconButton(onClick = { visible = !visible }) {
                    Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (visible) "Hide value" else "Show value", modifier = Modifier.size(20.dp))
                }
            }) else trailing,
        )
    }
}

/** Numeric input bound to nullable Long. Bump [resync] to force external updates in. */
@Composable
fun NumberField(
    label: String,
    value: Long?,
    onChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    suffix: String? = null,
    resync: Any? = null,
    allowNegative: Boolean = false,
) {
    var text by remember { mutableStateOf(value?.toString() ?: "") }
    LaunchedEffect(value, resync) {
        val parsed = text.toLongOrNull()
        if (value != parsed) text = value?.toString() ?: ""
    }
    GlassTextField(
        label = label,
        value = text,
        onValueChange = { raw ->
            val normalized = raw.map { c -> c.digitToIntOrNull()?.toString() ?: c.toString() }.joinToString("")
            val filtered = normalized.filterIndexed { i, c -> c.isDigit() || (allowNegative && i == 0 && c == '-') }
            text = filtered
            onChange(filtered.toLongOrNull())
        },
        hint = hint,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        trailing = suffix?.let { suf ->
            { Text(suf, color = LocalGlass.current.textFaint, fontSize = 12.sp, modifier = Modifier.padding(end = 6.dp)) }
        },
    )
}

/** Duration input: shows plain number, stores "<n>u" strings (e.g. "5s","30m"). */
@Composable
fun DurationField(
    label: String,
    raw: String?,
    unit: Char = 's',
    fallbackSeconds: Long? = null,
    onChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(raw?.trimEnd('s', 'm', 'h')?.toLongOrNull()?.toString() ?: "") }
    LaunchedEffect(raw) {
        val shown = raw?.trimEnd('s', 'm', 'h')?.toLongOrNull()?.toString() ?: ""
        if (text.toLongOrNull() != raw?.trimEnd('s','m','h')?.toLongOrNull()) text = shown
    }
    GlassTextField(
        label = label,
        value = text,
        onValueChange = { r ->
            text = r.filter(Char::isDigit)
            val n = text.toLongOrNull()
            onChange(
                when {
                    n != null && n > 0 -> "$n$unit"
                    fallbackSeconds != null -> "${fallbackSeconds}$unit"
                    else -> null
                }
            )
        },
        modifier = modifier,
        trailing = { Text(unit.toString(), color = LocalGlass.current.textFaint, fontSize = 12.sp, modifier = Modifier.padding(end = 6.dp)) },
    )
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    val g = LocalGlass.current
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .toggleable(value = checked, role = Role.Switch, onValueChange = { onChange(it); JsonEditSignal.bump() })
            .padding(horizontal = 4.dp, vertical = 4.dp).heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(UiLocale.text(label), color = g.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(UiLocale.text(subtitle), color = g.textFaint, fontSize = 11.sp, lineHeight = 16.sp)
        }
        Switch(checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = g.teal, checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                uncheckedTrackColor = g.innerFill, uncheckedThumbColor = g.textFaint, uncheckedBorderColor = g.strokeHi,
            ))
    }
}

// ---------------------------------------------------------------- selects

data class Opt<T>(val label: String, val value: T)

@Composable
fun <T : Any> SelectField(
    label: String,
    value: T?,
    options: List<Opt<T>>,
    onChange: (T?) -> Unit,
    modifier: Modifier = Modifier,
    clearable: Boolean = true,
) {
    val g = LocalGlass.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().innerFill().clickable { open = !open }.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(UiLocale.text(label), color = g.textFaint, fontSize = 11.sp, letterSpacing = 0.4.sp)
                Spacer(Modifier.height(1.dp))
                val selOpt = options.firstOrNull { it.value == value }
                Text(
                    selOpt?.label?.let(UiLocale::text) ?: value?.toString().orEmpty(),
                    color = if (selOpt != null) g.text else g.textFaint.copy(alpha = 0.6f),
                    fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Filled.ArrowDropDown, null, tint = g.textFaint)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = g.surface, shape = RoundedCornerShape(16.dp)) {
            if (clearable) {
                DropdownMenuItem(text = { Text("— clear —", color = g.textFaint, fontSize = 13.sp) }, onClick = { open = false; onChange(null); JsonEditSignal.bump() })
            }
            options.forEach { opt ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(UiLocale.text(opt.label), color = g.text, fontSize = 14.sp)
                            if (value == opt.value) {
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Filled.Check, null, tint = g.teal, modifier = Modifier.size(15.dp))
                            }
                        }
                    },
                    onClick = { open = false; onChange(opt.value); JsonEditSignal.bump() },
                )
            }
        }
    }
}

/** Multi select opening a bottom sheet; values are Strings. */
@Composable
fun MultiSelectField(
    label: String,
    selected: Set<String>,
    options: List<Opt<String>>,
    onChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier,
    displayLimit: Int = 3,
    searchable: Boolean = false,
) {
    val g = LocalGlass.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().innerFill().clickable { open = true }.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(UiLocale.text(label), color = g.textFaint, fontSize = 11.sp, letterSpacing = 0.4.sp)
                Spacer(Modifier.height(1.dp))
                val selLabels = options.filter { it.value in selected }.map { it.label }
                val summary = when {
                    selLabels.isEmpty() -> ""
                    selLabels.size <= displayLimit -> selLabels.joinToString(", ")
                    else -> "${selLabels.take(displayLimit).joinToString(", ")} +${selLabels.size - displayLimit}"
                }
                Text(summary, color = if (summary.isEmpty()) g.textFaint.copy(alpha = 0.6f) else g.text, fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (selected.isNotEmpty()) {
                IconButton(onClick = { onChange(emptySet()); JsonEditSignal.bump() }) {
                    Icon(Icons.Filled.Close, null, tint = g.textFaint, modifier = Modifier.size(15.dp))
                }
            }
        }
        if (open) {
            var query by remember { mutableStateOf("") }
            val shown = options.filter { it.label.contains(query, ignoreCase = true) }
            ModalBottomSheet(onDismissRequest = { open = false }, containerColor = g.surface) {
                Text(UiLocale.text(label), color = g.textDim, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 22.dp, vertical = 6.dp))
                if (searchable) GlassTextField("Search", query, { query = it }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                Column(Modifier.padding(horizontal = 10.dp).verticalScroll(rememberScrollState())) {
                    if (options.isEmpty()) Text("no options available", color = g.textFaint, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                    shown.forEach { opt ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                onChange(if (opt.value in selected) selected - opt.value else selected + opt.value)
                                JsonEditSignal.bump()
                            }.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = opt.value in selected,
                                onCheckedChange = { onChange(if (it) selected + opt.value else selected - opt.value); JsonEditSignal.bump() },
                                colors = CheckboxDefaults.colors(checkedColor = g.teal, checkmarkColor = Color.Black),
                            )
                            Text(opt.label, color = g.text, fontSize = 14.sp)
                        }
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

/** Free-form chips entry (combobox multiple) via dialog. */
@Composable
fun ChipsField(
    label: String,
    values: List<String>,
    onChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    suggestions: List<String> = emptyList(),
) {
    val g = LocalGlass.current
    var open by remember { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().innerFill().clickable { open = true; draft = "" }.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, color = g.textFaint, fontSize = 11.sp, letterSpacing = 0.4.sp)
                Spacer(Modifier.height(1.dp))
                Text(
                    if (values.isEmpty()) "" else values.joinToString(", "),
                    color = if (values.isEmpty()) g.textFaint.copy(alpha = 0.6f) else g.text,
                    fontSize = 14.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Filled.Add, null, tint = g.textFaint)
        }
        if (open) {
            AlertDialog(
                onDismissRequest = { open = false },
                containerColor = g.surface,
                title = { Text(label, color = g.text, fontSize = 17.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GlassTextField("Add item", draft, { draft = it }, hint = "type…", modifier = Modifier.weight(1f))
                            IconGhostButton(Icons.Filled.Add, {
                                val v = draft.trim()
                                if (v.isNotEmpty() && v !in values) { onChange(values + v); JsonEditSignal.bump() }
                                draft = ""
                            })
                        }
                        if (values.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                values.forEach { v ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(v, color = g.text, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        IconButton(onClick = { onChange(values - v); JsonEditSignal.bump() }) {
                                            Icon(Icons.Rounded.DeleteOutline, null, tint = g.err, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        } else Text("empty", color = g.textFaint, fontSize = 12.sp)
                    }
                },
                confirmButton = { TextButton(onClick = { open = false }) { Text("Done", color = g.blue) } },
            )
        }
    }
}

// ------------------------------------------------------------- key/value map

fun jsonObjectEntries(o: JSONObject?): List<Pair<String, String>> {
    o ?: return emptyList()
    return o.keys().asSequence().map { k ->
        val v = o.opt(k)
        k to when (v) {
            is JSONArray -> (0 until v.length()).map { v.opt(it)?.toString() ?: "" }.joinToString(",")
            is JSONObject -> v.toString()
            JSONObject.NULL, null -> ""
            else -> v.toString()
        }
    }.toList()
}

fun entriesToJsonObject(entries: List<Pair<String, String>>): JSONObject? {
    if (entries.isEmpty()) return null
    val o = JSONObject()
    val grouped = entries.groupBy({ it.first.trim() }, { it.second }).filterKeys { it.isNotBlank() }
    for ((k, vs) in grouped) {
        val clean = vs.filter { it.isNotBlank() }
        when {
            clean.isEmpty() -> {}
            clean.size == 1 -> o.put(k, clean[0])
            else -> o.put(k, JSONArray(clean))
        }
    }
    return if (o.length() == 0) null else o
}

@Composable
fun KeyValueEditor(
    title: String,
    entries: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    onChange: (List<Pair<String, String>>) -> Unit,
) {
    val g = LocalGlass.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title) {
            IconGhostButton(Icons.Filled.Add, { onChange(entries + ("Host" to "")) })
        }
        entries.forEachIndexed { i, (k, v) ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassTextField("Key", k, { nv -> onChange(entries.toMutableList().also { it[i] = nv to v }) }, modifier = Modifier.weight(0.42f))
                GlassTextField("Value", v, { nv -> onChange(entries.toMutableList().also { it[i] = k to nv }) }, modifier = Modifier.weight(0.58f))
                IconButton(onClick = { onChange(entries.filterIndexed { idx, _ -> idx != i }); JsonEditSignal.bump() }) {
                    Icon(Icons.Rounded.DeleteOutline, null, tint = g.err, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- dialogs

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "Delete",
    danger: Boolean = true,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val g = LocalGlass.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = g.surface,
        title = { Text(UiLocale.text(title), color = g.text, fontSize = 17.sp, fontWeight = FontWeight.Bold) },
        text = { Text(UiLocale.digits(message), color = g.textDim, fontSize = 14.sp) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmText, color = if (danger) g.err else g.blue, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = g.textDim) } },
    )
}

@Composable
fun MessageDialog(title: String, message: String, onDismiss: () -> Unit, mono: Boolean = false) {
    val g = LocalGlass.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = g.surface,
        title = { Text(title, color = g.text, fontSize = 17.sp, fontWeight = FontWeight.Bold) },
        text = {
            Text(message, color = g.textDim, fontSize = 13.sp, fontFamily = if (mono) FontFamily.Monospace else null)
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = g.blue) } },
    )
}

// ---------------------------------------------------------------- toasts

object ToastBus {
    val messages = mutableStateListOf<Pair<Long, String>>()

    fun show(msg: String) {
        messages.add(System.nanoTime() to msg)
        if (messages.size > 3) messages.removeAt(0)
    }

    fun sweep(id: Long) {
        messages.removeAll { it.first == id }
    }
}

    @Composable
    fun ToastHost() {
        val g = LocalGlass.current
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.padding(bottom = 96.dp).fillMaxWidth().padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToastBus.messages.toList().forEach { (id, msg) ->
                    LaunchedEffect(id) {
                        delay(2600)
                        ToastBus.sweep(id)
                    }
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(id) { visible = true }
                    AnimatedVisibility(
                        visible = visible,
                        exit = slideOutVertically(targetOffsetY = { it / 2 }, animationSpec = tween(180)) + fadeOut(tween(160)) + scaleOut(targetScale = 0.92f),
                    ) {
                        Row(
                            Modifier
                                .pressScaleBreathe()
                                .glassSurface(corner = 16.dp, alphaHi = 0.24f, alphaLo = 0.18f, elevation = 5.dp)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(msg, color = g.text, fontSize = 13.5.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }

/** Gentle pop-in used by toasts. */
private fun Modifier.pressScaleBreathe(): Modifier = composed {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.85f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "toastIn",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

// ---------------------------------------------------------------- misc

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String? = null, action: (@Composable () -> Unit)? = null) {
    val g = LocalGlass.current
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(74.dp).glassSurface(corner = 26.dp, alphaHi = 0.10f, alphaLo = 0.04f),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = g.violet, modifier = Modifier.size(34.dp)) }
        Text(UiLocale.text(title), color = g.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (subtitle != null) Text(UiLocale.text(subtitle), color = g.textFaint, fontSize = 13.sp, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

@Composable
fun BusyOverlay(busy: Boolean, label: String = "One moment") {
    val g = LocalGlass.current
    AnimatedVisibility(busy, enter = androidx.compose.animation.fadeIn(tween(160)), exit = fadeOut(tween(120))) {
        Box(
            Modifier.fillMaxSize().background(g.bgBottom.copy(alpha = 0.72f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            RequestCard(label.ifBlank { "One moment" }, "Waiting for your panel to respond.")
        }
    }
}
