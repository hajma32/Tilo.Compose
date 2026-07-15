package tilo.samples

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun BoxScope.SampleInfoCard(
    sample: Sample,
    body: String,
    code: String,
) {
    Surface(
        modifier = Modifier.align(Alignment.TopStart).padding(16.dp).widthIn(max = 265.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .96f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(3.dp),
        shadowElevation = 3.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "${sample.number} / ${sample.title.uppercase()}",
                color = Orange,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                letterSpacing = .7.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = sample.title,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                letterSpacing = (-0.7).sp,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(12.dp))
            Surface(
                color = if (MaterialTheme.colorScheme.background == Paper) PaperRaised else Color(0xFF0D1511),
                shape = RoundedCornerShape(2.dp),
            ) {
                Text(
                    text = code,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun BoxScope.MapPill(text: String) {
    Surface(
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 12.dp, bottom = 84.dp),
        color = Ink.copy(alpha = .93f),
        contentColor = Color.White,
        border = BorderStroke(1.dp, Color(0xFF526159)),
        shape = RoundedCornerShape(3.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
    }
}
