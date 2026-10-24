package app.cheshers.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class OnboardingPage(val glyph: String, val title: String, val body: String)
private val pages = listOf(
    OnboardingPage("♞", "Welcome to Cheshers", "A complete chess home with no ads, subscriptions, or premium locks. Play offline, online, and against OpenRouter-powered bots."),
    OnboardingPage("♟", "Your game, your way", "Choose six bot levels and three personalities, use time controls, solve daily puzzles, review PGNs, and earn cosmetic rewards."),
    OnboardingPage("✓", "Fair rules, resilient play", "Chesslib validates every move. If AI is unavailable, offline games continue with random legal moves. Your games and progress stay on your device."),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth()) { Spacer(Modifier.weight(1f)); TextButton(onClick = onDone) { Text("Skip") } }
            HorizontalPager(pager, modifier = Modifier.weight(1f)) { page ->
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(pages[page].glyph, style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
                    Text(pages[page].title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 18.dp))
                    Text(pages[page].body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                pages.indices.forEach { index ->
                    Box(Modifier.size(if (pager.currentPage == index) 10.dp else 7.dp).background(if (pager.currentPage == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape))
                }
            }
            Button(
                onClick = { if (pager.currentPage == pages.lastIndex) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                modifier = Modifier.fillMaxWidth().padding(top = 22.dp).height(52.dp),
            ) { Text(if (pager.currentPage == pages.lastIndex) "Start playing" else "Next") }
        }
    }
}
