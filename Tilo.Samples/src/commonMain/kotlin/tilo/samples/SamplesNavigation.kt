package tilo.samples

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SamplesTopBar(onMenuClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.statusBarsPadding().height(68.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(42.dp).clickable(onClick = onMenuClick)) {
                HamburgerIcon(Modifier.fillMaxSize().padding(11.dp))
            }
            Spacer(Modifier.width(14.dp))
            TiloMapLogo()
            Spacer(Modifier.width(11.dp))
            Text(
                text = "Tilo.Compose",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                letterSpacing = (-0.5).sp,
            )
        }
    }
}

@Composable
internal fun SamplesDrawer(
    selectedSample: Sample,
    onSelect: (Sample) -> Unit,
) {
    ModalDrawerSheet(
        modifier = Modifier.width(304.dp).fillMaxHeight(),
        drawerShape = RectangleShape,
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        drawerContentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
            DrawerHeader()
            Divider()

            SampleSection.entries.forEach { section ->
                SectionTitle(section.title)
                Sample.entries.filter { it.section == section }.forEach { sample ->
                    SampleDrawerItem(
                        sample = sample,
                        selected = sample == selectedSample,
                        onClick = { onSelect(sample) },
                    )
                }
            }

            Spacer(Modifier.height(30.dp).weight(1f))
            Divider()
            DrawerFooter()
        }
    }
}

@Composable
private fun DrawerHeader() {
    Column(Modifier.padding(start = 28.dp, top = 28.dp, end = 24.dp, bottom = 22.dp)) {
        Text(
            text = "PLAYFUL MAPS, SERIOUS COORDINATES",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 0.7.sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Tilo Samples",
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 23.sp,
            letterSpacing = (-0.8).sp,
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(start = 28.dp, top = 24.dp, bottom = 9.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        letterSpacing = 1.1.sp,
    )
}

@Composable
private fun SampleDrawerItem(
    sample: Sample,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 2.dp)
                .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
                .clickable(onClick = onClick)
                .height(58.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(if (selected) Orange else Color.Transparent),
        )
        Text(
            text = sample.number,
            modifier = Modifier.padding(start = 11.dp),
            color = Orange,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        Column(Modifier.padding(start = 12.dp, end = 8.dp)) {
            Text(
                text = sample.title,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.sp,
            )
            Text(
                text = sample.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun DrawerFooter() {
    Column(Modifier.padding(28.dp)) {
        Text(
            text = "ALPHA · APIs MAY CHANGE",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(text = "Built with the public Tilo API.", fontSize = 13.sp)
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

@Composable
private fun TiloMapLogo() {
    val stroke = MaterialTheme.colorScheme.onBackground
    Canvas(Modifier.size(30.dp)) {
        val left =
            Path().apply {
                moveTo(size.width * .06f, size.height * .17f)
                lineTo(size.width * .34f, size.height * .28f)
                lineTo(size.width * .34f, size.height * .91f)
                lineTo(size.width * .06f, size.height * .80f)
                close()
            }
        val center =
            Path().apply {
                moveTo(size.width * .34f, size.height * .28f)
                lineTo(size.width * .64f, size.height * .10f)
                lineTo(size.width * .64f, size.height * .73f)
                lineTo(size.width * .34f, size.height * .91f)
                close()
            }
        val right =
            Path().apply {
                moveTo(size.width * .64f, size.height * .10f)
                lineTo(size.width * .94f, size.height * .24f)
                lineTo(size.width * .94f, size.height * .87f)
                lineTo(size.width * .64f, size.height * .73f)
                close()
            }
        listOf(
            left to Color(0xFFFFC09C),
            center to Color(0xFFF58255),
            right to Color(0xFFD95532),
        ).forEach { (path, color) ->
            drawPath(path, color)
            drawPath(path, stroke, style = Stroke(width = 1.4.dp.toPx()))
        }
    }
}

@Composable
private fun HamburgerIcon(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onBackground
    Canvas(modifier) {
        listOf(.25f, .5f, .75f).forEach { y ->
            drawLine(
                color = color,
                start = Offset(0f, size.height * y),
                end = Offset(size.width, size.height * y),
                strokeWidth = 1.75.dp.toPx(),
            )
        }
    }
}
