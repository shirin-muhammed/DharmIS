package co.ocaana.dharmis

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val pal: Pal @Composable get() = LocalPal.current

@Composable
fun T(text: String, size: Int = 15, weight: FontWeight = FontWeight.Medium, color: Color = pal.ink, modifier: Modifier = Modifier, align: TextAlign? = null, lh: Int? = null) {
    Text(text, modifier, color = color, fontSize = size.sp, fontWeight = weight, textAlign = align, lineHeight = (lh ?: (size * 13 / 10 + 2)).sp)
}

val Bold = FontWeight.Bold
val Heavy = FontWeight.ExtraBold
val Semi = FontWeight.SemiBold

@Composable
fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(pal.bg).statusBarsPadding().navigationBarsPadding().imePadding(), content = content)
}

@Composable
fun Card(modifier: Modifier = Modifier, bg: Color = pal.card, border: Color? = pal.line, pad: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(modifier.clip(shape).background(bg).let { if (border != null) it.border(BorderStroke(1.dp, border), shape) else it }.padding(pad), content = content)
}

/** kind: 0 primary, 1 outlined, 2 red */
@Composable
fun BigButton(text: String, icon: ImageVector? = null, kind: Int = 0, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val bg = when (kind) { 0 -> pal.pri; 2 -> pal.red; else -> pal.card }
    val fg = when (kind) { 0 -> pal.onPri; 2 -> pal.onRed; else -> pal.ink }
    val shape = RoundedCornerShape(30.dp)
    Row(
        modifier.height(60.dp).clip(shape).background(bg.copy(alpha = if (enabled) 1f else .4f))
            .let { if (kind == 1) it.border(BorderStroke(2.dp, pal.line), shape) else it }
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(10.dp)) }
        T(text, 17, Bold, fg)
    }
}

@Composable
fun Pill(text: String, bg: Color, fg: Color, icon: ImageVector? = null) {
    Row(Modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)) }
        T(text, 12, Bold, fg)
    }
}

@Composable
fun OptionRow(title: String, sub: String? = null, icon: ImageVector? = null, selected: Boolean = false, chevron: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(shape).background(if (selected) pal.pri2 else pal.card)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) pal.pri else pal.line), shape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = pal.pri, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) { T(title, 17, Bold); if (sub != null) T(sub, 13, FontWeight.Normal, pal.ink3) }
        if (selected) Icon(Icons.Rounded.Check, null, tint = pal.pri, modifier = Modifier.size(22.dp))
        else if (chevron) Icon(Icons.Rounded.ChevronRight, null, tint = pal.ink3, modifier = Modifier.size(20.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Chips(items: List<String>, selected: Set<String>, onTap: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { s ->
            val on = s in selected
            Box(
                Modifier.height(48.dp).clip(RoundedCornerShape(24.dp)).background(if (on) pal.pri else pal.bg)
                    .border(BorderStroke(1.dp, if (on) pal.pri else pal.line), RoundedCornerShape(24.dp))
                    .clickable { onTap(s) }.padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) { T(s, 16, Bold, if (on) pal.onPri else pal.ink) }
        }
    }
}

@Composable
fun Counter(label: String, sub: String, value: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(pal.bg).border(BorderStroke(1.dp, pal.line), RoundedCornerShape(18.dp)).padding(start = 18.dp, top = 8.dp, bottom = 8.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { T(label, 16, Bold); T(sub, 12, FontWeight.Normal, pal.ink3) }
        RoundBtn(Icons.Rounded.Remove, false) { if (value > 0) onChange(value - 1) }
        Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) { T(value.toString(), 26, Heavy) }
        RoundBtn(Icons.Rounded.Add, true) { onChange(value + 1) }
    }
}

@Composable
fun RoundBtn(icon: ImageVector, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(if (filled) pal.pri else pal.card).border(BorderStroke(1.dp, pal.line), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (filled) pal.onPri else pal.ink, modifier = Modifier.size(24.dp)) }
}

@Composable
fun Field(label: String, value: String, hint: String = "", keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        T(label, 14, Bold, pal.ink2)
        TextBox(value, hint, null, keyboard, onChange)
    }
}

@Composable
fun TextBox(value: String, hint: String, icon: ImageVector? = null, keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(Modifier.fillMaxWidth().height(56.dp).clip(shape).background(pal.card).border(BorderStroke(1.dp, pal.line), shape).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = pal.ink3, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)) }
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) T(hint, 17, FontWeight.Normal, pal.ink3)
            BasicTextField(value, onChange, singleLine = true, textStyle = TextStyle(color = pal.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold), cursorBrush = SolidColor(pal.pri), keyboardOptions = KeyboardOptions(keyboardType = keyboard), modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun BackBar(title: String, sub: String? = null, icon: ImageVector = Icons.AutoMirrored.Rounded.ArrowBack, trailing: (@Composable () -> Unit)? = null, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) { Icon(icon, "Back", tint = pal.ink, modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) { T(title, 18, Heavy); if (sub != null) T(sub, 12, FontWeight.Medium, pal.ink3) }
        trailing?.invoke()
    }
}
