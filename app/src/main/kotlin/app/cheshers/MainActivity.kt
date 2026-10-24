package app.cheshers

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.cheshers.core.chess.GameMode
import app.cheshers.core.data.repository.CheshersSettings
import app.cheshers.feature.game.GameScreen
import app.cheshers.feature.game.GameSetupScreen
import app.cheshers.feature.game.SideChoice
import app.cheshers.feature.game.analysis.AnalysisScreen
import app.cheshers.feature.game.puzzle.PuzzleMenuScreen
import app.cheshers.feature.game.puzzle.PuzzleScreen
import app.cheshers.feature.online.OnlineGameScreen
import app.cheshers.feature.online.OnlineLobbyScreen
import app.cheshers.feature.profile.ProfileScreen
import app.cheshers.feature.quests.QuestScreen
import app.cheshers.ui.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            CheshersTheme(settings ?: CheshersSettings()) {
                val current = settings
                if (current == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else CheshersNavigation(current, viewModel)
            }
        }
    }
}

@Composable
private fun CheshersNavigation(settings: CheshersSettings, mainViewModel: MainViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(settings.onboardingComplete) {
        if (settings.onboardingComplete && settings.notifications && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val start = if (settings.onboardingComplete) "home" else "onboarding"
    NavHost(navController, startDestination = start) {
        composable("onboarding") {
            OnboardingScreen(onDone = {
                mainViewModel.completeOnboarding()
                navController.navigate("home") { popUpTo(0) { inclusive = true } }
            })
        }
        composable("home") {
            HomeScreen(
                onNavigate = { route -> navController.navigate(route) },
                onContinue = { game ->
                    if (game.mode == GameMode.ONLINE.name) navController.navigate("online-game/${game.id.removePrefix("online-")}")
                    else navController.navigate("resume/${game.id}")
                },
            )
        }
        composable("offline-setup") {
            GameSetupScreen(
                onBack = navController::popBackStack,
                onStart = { mode, difficulty, side, time, personality ->
                    navController.navigate("game/${mode.name}/${difficulty.name}/${side.name}/${time.id}/${personality.name}")
                },
            )
        }
        composable("game/{mode}/{difficulty}/{side}/{time}/{personality}") { entry ->
            val args = requireNotNull(entry.arguments)
            GameScreen(
                mode = GameMode.valueOf(requireNotNull(args.getString("mode"))),
                difficulty = app.cheshers.core.chess.Difficulty.valueOf(requireNotNull(args.getString("difficulty"))),
                sideChoice = SideChoice.valueOf(requireNotNull(args.getString("side"))),
                timeControl = app.cheshers.core.chess.TimeControl.fromId(requireNotNull(args.getString("time"))),
                personality = app.cheshers.core.chess.Personality.valueOf(requireNotNull(args.getString("personality"))),
                onBack = navController::popBackStack,
                onNewGame = { navController.navigate("offline-setup") { popUpTo("home") } },
                onReview = { id -> navController.navigate("review/$id") },
            )
        }
        composable("resume/{activeId}") { entry ->
            GameScreen(
                activeGameId = entry.arguments?.getString("activeId"),
                onBack = navController::popBackStack,
                onNewGame = { navController.navigate("offline-setup") { popUpTo("home") } },
                onReview = { id -> navController.navigate("review/$id") },
            )
        }
        composable("online") {
            OnlineLobbyScreen(onBack = navController::popBackStack, onOpenGame = { id -> navController.navigate("online-game/$id") })
        }
        composable("online-game/{gameId}") { entry ->
            OnlineGameScreen(
                gameId = requireNotNull(entry.arguments?.getString("gameId")),
                onBack = navController::popBackStack,
                onReview = { id -> navController.navigate("review/$id") },
            )
        }
        composable("puzzles") {
            PuzzleMenuScreen(
                onBack = navController::popBackStack,
                onDaily = { navController.navigate("daily-puzzle") },
                onPuzzle = { id -> navController.navigate("puzzle/$id") },
            )
        }
        composable("puzzle/{puzzleId}") { entry ->
            PuzzleScreen(puzzleId = entry.arguments?.getString("puzzleId"), onBack = navController::popBackStack)
        }
        composable("daily-puzzle") { PuzzleScreen(daily = true, onBack = navController::popBackStack) }
        composable("analysis") {
            AnalysisScreen(onBack = navController::popBackStack, onSharePgn = { sharePgn(context, it) })
        }
        composable("review/{gameId}") { entry ->
            AnalysisScreen(gameId = entry.arguments?.getString("gameId"), onBack = navController::popBackStack, onSharePgn = { sharePgn(context, it) })
        }
        composable("quests") { QuestScreen(onBack = navController::popBackStack) }
        composable("profile") {
            ProfileScreen(
                onBack = navController::popBackStack,
                onReplay = { id -> navController.navigate("review/$id") },
                onSharePgn = { sharePgn(context, it) },
            )
        }
        composable("settings") {
            SettingsScreen(onBack = navController::popBackStack, onReplayOnboarding = { navController.navigate("onboarding") })
        }
    }
}
